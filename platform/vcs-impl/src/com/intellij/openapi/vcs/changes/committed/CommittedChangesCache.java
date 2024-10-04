// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.vcs.ProgressManagerQueue;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.MessageBusUtil.invokeLaterIfNeededOnSyncPublisher;
import static com.intellij.util.containers.ContainerUtil.unmodifiableOrEmptyList;

@Service(Service.Level.PROJECT)
@State(name = "CommittedChangesCache", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class CommittedChangesCache extends SimplePersistentStateComponent<CommittedChangesCacheState> {
  private static final Logger LOG = Logger.getInstance(CommittedChangesCache.class);

  private final Project myProject;
  private final ProgressManagerQueue myTaskQueue;
  private boolean myRefreshingIncomingChanges = false;
  private int myPendingUpdateCount = 0;
  private ScheduledFuture myFuture;
  private List<CommittedChangeList> myCachedIncomingChangeLists;
  private final @NotNull Set<CommittedChangeList> myNewIncomingChanges = new LinkedHashSet<>();

  private MyRefreshRunnable refreshRunnable;

  private final Map<String, Pair<Long, List<CommittedChangeList>>> myExternallyLoadedChangeLists;
  private final CachesHolder myCachesHolder;
  private final RepositoryLocationCache myLocationCache;

  @Topic.ProjectLevel
  public static final Topic<CommittedChangesListener> COMMITTED_TOPIC = new Topic<>(CommittedChangesListener.class, Topic.BroadcastDirection.NONE);

  public static CommittedChangesCache getInstance(Project project) {
    return project.getService(CommittedChangesCache.class);
  }

  @Nullable
  public static CommittedChangesCache getInstanceIfCreated(Project project) {
    return project.getServiceIfCreated(CommittedChangesCache.class);
  }

  public CommittedChangesCache(@NotNull Project project) {
    super(new CommittedChangesCacheState());
    myProject = project;
    VcsListener vcsListener = new VcsListener() {
      @Override
      public void directoryMappingChanged() {
        myLocationCache.reset();
        myCachesHolder.reset();
        refreshAllCachesAsync(false, true);
        refreshIncomingChangesAsync();
        myTaskQueue.run(() -> {
          for (ChangesCacheFile file : myCachesHolder.getAllCaches()) {
            final RepositoryLocation location = file.getLocation();
            fireChangesLoaded(location, Collections.emptyList());
          }
          fireIncomingReloaded();
        });
      }
    };
    myLocationCache = new RepositoryLocationCache(project);
    myCachesHolder = new CachesHolder(project, myLocationCache);
    myTaskQueue = new ProgressManagerQueue(project, VcsBundle.message("committed.changes.refresh.progress"));
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization(() -> {
      ApplicationManager.getApplication().runReadAction(() -> {
        if (myProject.isDisposed()) {
          return;
        }

        myTaskQueue.start();

        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, vcsListener);
        connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN, vcsListener);
      });
    });
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        cancelRefreshTimer();
      }
    });
    myExternallyLoadedChangeLists = new ConcurrentHashMap<>();
  }

  @ApiStatus.Internal
  @Override
  public void loadState(@NotNull CommittedChangesCacheState state) {
    super.loadState(state);
    updateRefreshTimer();
  }

  public boolean isMaxCountSupportedForProject() {
    for (AbstractVcs vcs : ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()) {
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      if (provider instanceof CachingCommittedChangesProvider cachingProvider) {
        if (!cachingProvider.isMaxCountSupported()) {
          return false;
        }
      }
    }
    return true;
  }

  private final class MyProjectChangesLoader implements Runnable {
    private final ChangeBrowserSettings mySettings;
    private final int myMaxCount;
    private final boolean myCacheOnly;
    private final Consumer<? super List<CommittedChangeList>> myConsumer;
    private final Consumer<? super List<VcsException>> myErrorConsumer;

    private final LinkedHashSet<CommittedChangeList> myResult = new LinkedHashSet<>();
    private final List<VcsException> myExceptions = new ArrayList<>();
    private boolean myDisposed = false;

    private MyProjectChangesLoader(ChangeBrowserSettings settings, int maxCount, boolean cacheOnly,
                                   Consumer<? super List<CommittedChangeList>> consumer, Consumer<? super List<VcsException>> errorConsumer) {
      mySettings = settings;
      myMaxCount = maxCount;
      myCacheOnly = cacheOnly;
      myConsumer = consumer;
      myErrorConsumer = errorConsumer;
    }

    @Override
    public void run() {
      for (AbstractVcs vcs : ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()) {
        final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
        if (provider == null) continue;

        final VcsCommittedListsZipper vcsZipper = provider.getZipper();
        CommittedListsSequencesZipper zipper = null;
        if (vcsZipper != null) {
          zipper = new CommittedListsSequencesZipper(vcsZipper);
        }
        boolean zipSupported = zipper != null;

        final Map<VirtualFile, RepositoryLocation> map = myCachesHolder.getAllRootsUnderVcs(vcs);

        for (VirtualFile root : map.keySet()) {
          if (myProject.isDisposed())  return;

          final RepositoryLocation location = map.get(root);

          try {
            final List<CommittedChangeList> lists = getChanges(mySettings, root, vcs, myMaxCount, myCacheOnly, provider, location);
            if (lists != null) {
              if (zipSupported) {
                zipper.add(location, lists);
              } else {
                myResult.addAll(lists);
              }
            }
          }
          catch (VcsException e) {
            myExceptions.add(e);
          }
          catch(ProcessCanceledException e) {
            myDisposed = true;
          }
        }

        if (zipSupported) {
          myResult.addAll(zipper.execute());
        }
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        LOG.info("FINISHED CommittedChangesCache.getProjectChangesAsync - execution in queue");
        if (myProject.isDisposed()) {
          return;
        }
        if (myExceptions.size() > 0) {
          myErrorConsumer.consume(myExceptions);
        }
        else if (!myDisposed) {
          myConsumer.consume(new ArrayList<>(myResult));
        }
      }, ModalityState.nonModal());
    }
  }

  public void getProjectChangesAsync(final ChangeBrowserSettings settings,
                                     final int maxCount,
                                     final boolean cacheOnly,
                                     final Consumer<? super List<CommittedChangeList>> consumer,
                                     final Consumer<? super List<VcsException>> errorConsumer) {
    final MyProjectChangesLoader loader = new MyProjectChangesLoader(settings, maxCount, cacheOnly, consumer, errorConsumer);
    myTaskQueue.run(loader);
  }

  public @Nullable List<CommittedChangeList> getChanges(ChangeBrowserSettings settings, final VirtualFile file, final @NotNull AbstractVcs vcs,
                                                        final int maxCount, final boolean cacheOnly, final CommittedChangesProvider provider,
                                                        final RepositoryLocation location) throws VcsException {
    if (settings instanceof CompositeCommittedChangesProvider.CompositeChangeBrowserSettings) {
      settings = ((CompositeCommittedChangesProvider.CompositeChangeBrowserSettings) settings).get(vcs);
    }
    if (provider instanceof CachingCommittedChangesProvider) {
      try {
        if (cacheOnly) {
          ChangesCacheFile cacheFile = myCachesHolder.getCacheFile(vcs, file, location);
          if (!cacheFile.isEmpty()) {

            final RepositoryLocation fileLocation = cacheFile.getLocation();
            fileLocation.onBeforeBatch();
            final List<CommittedChangeList> committedChangeLists = cacheFile.readChanges(settings, maxCount);
            fileLocation.onAfterBatch();
            return committedChangeLists;
          }
          return null;
        }
        else {
          if (canGetFromCache(vcs, settings, file, location, maxCount)) {
            return getChangesWithCaching(vcs, settings, file, location, maxCount);
          }
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    //noinspection unchecked
    return provider.getCommittedChanges(settings, location, maxCount);
  }

  private boolean canGetFromCache(final AbstractVcs vcs, final ChangeBrowserSettings settings,
                                  final VirtualFile root, final RepositoryLocation location, final int maxCount) throws IOException {
    ChangesCacheFile cacheFile = myCachesHolder.getCacheFile(vcs, root, location);
    if (cacheFile.isEmpty()) {
      return true;   // we'll initialize the cache and check again after that
    }
    if (settings.USE_DATE_BEFORE_FILTER && !settings.USE_DATE_AFTER_FILTER) {
      return cacheFile.hasCompleteHistory();
    }
    if (settings.USE_CHANGE_BEFORE_FILTER && !settings.USE_CHANGE_AFTER_FILTER) {
      return cacheFile.hasCompleteHistory();
    }

    boolean hasDateFilter = settings.USE_DATE_AFTER_FILTER || settings.USE_CHANGE_AFTER_FILTER;
    boolean hasNonDateFilter = settings.isNonDateFilterSpecified();
    if (!hasDateFilter && hasNonDateFilter) {
      return cacheFile.hasCompleteHistory();
    }
    if (settings.USE_DATE_AFTER_FILTER && settings.getDateAfter().getTime() < cacheFile.getFirstCachedDate().getTime()) {
      return cacheFile.hasCompleteHistory();
    }
    if (settings.USE_CHANGE_AFTER_FILTER && settings.getChangeAfterFilter().longValue() < cacheFile.getFirstCachedChangelist()) {
      return cacheFile.hasCompleteHistory();
    }
    return true;
  }

  public void hasCachesForAnyRoot(final @Nullable Consumer<? super Boolean> continuation) {
    myTaskQueue.run(() -> {
      final Ref<Boolean> success = new Ref<>();
      try {
        success.set(hasCachesWithEmptiness(false));
      }
      catch (ProcessCanceledException e) {
        success.set(true);
      }
      ApplicationManager.getApplication().invokeLater(() -> continuation.consume(success.get()), myProject.getDisposed());
    });
  }

  public boolean hasEmptyCaches() {
    try {
      return hasCachesWithEmptiness(true);
    }
    catch (ProcessCanceledException e) {
      return false;
    }
  }

  private boolean hasCachesWithEmptiness(final boolean emptiness) {
    final Ref<Boolean> resultRef = new Ref<>(Boolean.FALSE);
    myCachesHolder.iterateAllCaches(changesCacheFile -> {
      try {
        if (changesCacheFile.isEmpty() == emptiness) {
          resultRef.set(true);
          return false;
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return true;
    });
    return resultRef.get();
  }

  public @Nullable Iterator<ChangesBunch> getBackBunchedIterator(final AbstractVcs vcs, final VirtualFile root, final RepositoryLocation location, final int bunchSize) {
    final ChangesCacheFile cacheFile = myCachesHolder.getCacheFile(vcs, root, location);
    try {
      if (! cacheFile.isEmpty()) {
        return cacheFile.getBackBunchedIterator(bunchSize);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  private List<CommittedChangeList> getChangesWithCaching(final AbstractVcs vcs,
                                                          final ChangeBrowserSettings settings,
                                                          final VirtualFile root,
                                                          final RepositoryLocation location,
                                                          final int maxCount) throws VcsException, IOException {
    ChangesCacheFile cacheFile = myCachesHolder.getCacheFile(vcs, root, location);
    if (cacheFile.isEmpty()) {
      List<CommittedChangeList> changes = initCache(cacheFile);
      if (canGetFromCache(vcs, settings, root, location, maxCount)) {
        settings.filterChanges(changes);
        return trimToSize(changes, maxCount);
      }
      //noinspection unchecked
      return cacheFile.getProvider().getCommittedChanges(settings, location, maxCount);
    }
    else {
      // we take location instance that would be used for deserialization
      final RepositoryLocation fileLocation = cacheFile.getLocation();
      fileLocation.onBeforeBatch();
      final List<CommittedChangeList> changes = cacheFile.readChanges(settings, maxCount);
      fileLocation.onAfterBatch();
      List<CommittedChangeList> newChanges = refreshCache(cacheFile);
      settings.filterChanges(newChanges);
      changes.addAll(newChanges);
      return trimToSize(changes, maxCount);
    }
  }

  @TestOnly
  public void refreshAllCaches() throws IOException, VcsException {
    debug("Start refreshing all caches");
    final Collection<ChangesCacheFile> files = myCachesHolder.getAllCaches();
    debug(files.size() + " caches found");
    for(ChangesCacheFile file: files) {
      if (file.isEmpty()) {
        initCache(file);
      }
      else {
        refreshCache(file);
      }
    }
    debug("Finished refreshing all caches");
  }

  private @NotNull List<CommittedChangeList> initCache(@NotNull ChangesCacheFile cacheFile) throws VcsException, IOException {
    debug("Initializing cache for " + cacheFile.getLocation());
    final CachingCommittedChangesProvider provider = cacheFile.getProvider();
    final RepositoryLocation location = cacheFile.getLocation();
    final ChangeBrowserSettings settings = provider.createDefaultSettings();
    int maxCount = 0;
    if (isMaxCountSupportedForProject()) {
      maxCount = getState().getInitialCount();
    }
    else {
      settings.USE_DATE_AFTER_FILTER = true;
      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.DAY_OF_YEAR, -getState().getInitialDays());
      settings.setDateAfter(calendar.getTime());
    }
    //noinspection unchecked
    final List<CommittedChangeList> changes = provider.getCommittedChanges(settings, location, maxCount);
    // when initially initializing cache, assume all changelists are locally available
    writeChangesInReadAction(cacheFile, changes); // this sorts changes in chronological order
    if (maxCount > 0 && changes.size() < getState().getInitialCount()) {
      cacheFile.setHaveCompleteHistory(true);
    }
    if (changes.size() > 0) {
      fireChangesLoaded(location, changes);
    }
    return changes;
  }

  private void fireChangesLoaded(@NotNull RepositoryLocation location, @NotNull List<CommittedChangeList> changes) {
    invokeLaterIfNeededOnSyncPublisher(myProject, COMMITTED_TOPIC, listener -> listener.changesLoaded(location, changes));
  }

  private void fireIncomingReloaded() {
    invokeLaterIfNeededOnSyncPublisher(myProject, COMMITTED_TOPIC,
                                       listener -> listener.incomingChangesUpdated(Collections.emptyList()));
  }

  // todo: fix - would externally loaded necessarily for file? i.e. just not efficient now
  private @NotNull List<CommittedChangeList> refreshCache(@NotNull ChangesCacheFile cacheFile) throws VcsException, IOException {
    debug("Refreshing cache for " + cacheFile.getLocation());
    final List<CommittedChangeList> newLists = new ArrayList<>();

    final CachingCommittedChangesProvider provider = cacheFile.getProvider();
    final RepositoryLocation location = cacheFile.getLocation();

    final Pair<Long, List<CommittedChangeList>> externalLists = myExternallyLoadedChangeLists.get(location.getKey());
    final long latestChangeList = getLatestListForFile(cacheFile);
    if ((externalLists != null) && (latestChangeList == externalLists.first.longValue())) {
      newLists.addAll(appendLoadedChanges(cacheFile, externalLists.second));
      myExternallyLoadedChangeLists.clear();
    }

    final ChangeBrowserSettings defaultSettings = provider.createDefaultSettings();
    int maxCount = 0;
    if (provider.refreshCacheByNumber()) {
      final long number = cacheFile.getLastCachedChangelist();
      debug("Refreshing cache for " + location + " since #" + number);
      if (number >= 0) {
        defaultSettings.CHANGE_AFTER = Long.toString(number);
        defaultSettings.USE_CHANGE_AFTER_FILTER = true;
      }
      else {
        maxCount = getState().getInitialCount();
      }
    }
    else {
      final Date date = cacheFile.getLastCachedDate();
      debug("Refreshing cache for " + location + " since " + date);
      defaultSettings.setDateAfter(date);
      defaultSettings.USE_DATE_AFTER_FILTER = true;
    }
    defaultSettings.STRICTLY_AFTER = true;
    final List<CommittedChangeList> newChanges = provider.getCommittedChanges(defaultSettings, location, maxCount);
    debug("Loaded " + newChanges.size() + " new changelists");
    newLists.addAll(appendLoadedChanges(cacheFile, newChanges));

    return newLists;
  }

  private static void debug(@NonNls String message) {
    LOG.debug(message);
  }

  private List<CommittedChangeList> appendLoadedChanges(@NotNull ChangesCacheFile cacheFile,
                                                        @NotNull List<? extends CommittedChangeList> newChanges) throws IOException {
    final List<CommittedChangeList> savedChanges = writeChangesInReadAction(cacheFile, newChanges);
    if (savedChanges.size() > 0) {
      fireChangesLoaded(cacheFile.getLocation(), savedChanges);
    }
    return savedChanges;
  }

  private static List<CommittedChangeList> writeChangesInReadAction(final ChangesCacheFile cacheFile,
                                                                    @NotNull List<? extends CommittedChangeList> newChanges)
    throws IOException {
    // ensure that changes are loaded before taking read action, to avoid stalling UI
    for (CommittedChangeList changeList : newChanges) {
      changeList.getChanges();
    }
    final Ref<IOException> ref = new Ref<>();
    final List<CommittedChangeList> savedChanges = ReadAction.compute(() -> {
      try {
        return cacheFile.writeChanges(newChanges);    // skip duplicates;
      }
      catch (IOException e) {
        ref.set(e);
        return null;
      }
    });
    if (!ref.isNull()) {
      throw ref.get();
    }
    return savedChanges;
  }

  private static @NotNull List<CommittedChangeList> trimToSize(@NotNull List<CommittedChangeList> changes, int maxCount) {
    if (maxCount > 0) {
      while (changes.size() > maxCount) {
        changes.remove(0);
      }
    }
    return changes;
  }

  public List<CommittedChangeList> loadIncomingChanges(boolean inBackground) {
    final List<CommittedChangeList> result = new ArrayList<>();
    final Collection<ChangesCacheFile> caches = myCachesHolder.getAllCaches();

    debug(caches.size() + " caches found");

    final MultiMap<AbstractVcs, Pair<RepositoryLocation, List<CommittedChangeList>>> byVcs =
      new MultiMap<>();

    for(ChangesCacheFile cache: caches) {
      try {
        if (inBackground && (! cache.getVcs().isVcsBackgroundOperationsAllowed(cache.getRootPath().getVirtualFile()))) continue;
        if (!cache.isEmpty()) {
          debug("Loading incoming changes for " + cache.getLocation());
          final List<CommittedChangeList> incomingChanges = cache.loadIncomingChanges();
          byVcs.putValue(cache.getVcs(), Pair.create(cache.getLocation(), incomingChanges));
        }
        else {
          debug("Empty cache found for " + cache.getLocation());
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    for (AbstractVcs vcs : byVcs.keySet()) {
      final CommittedChangesProvider committedChangesProvider = vcs.getCommittedChangesProvider();
      VcsCommittedListsZipper vcsZipper = committedChangesProvider.getZipper();
      if (vcsZipper != null) {
        final VcsCommittedListsZipper incomingZipper = new IncomingListsZipper(vcsZipper);
        final CommittedListsSequencesZipper zipper = new CommittedListsSequencesZipper(incomingZipper);
        for (Pair<RepositoryLocation, List<CommittedChangeList>> pair : byVcs.get(vcs)) {
          zipper.add(pair.getFirst(), pair.getSecond());
        }
        result.addAll(zipper.execute());
      } else {
        for (Pair<RepositoryLocation, List<CommittedChangeList>> pair : byVcs.get(vcs)) {
          result.addAll(pair.getSecond());
        }
      }
    }

    myCachedIncomingChangeLists = result;
    debug("Incoming changes loaded");
    fireIncomingChangesUpdated(result);
    return result;
  }

  private static final class IncomingListsZipper extends VcsCommittedListsZipperAdapter {
    private final VcsCommittedListsZipper myVcsZipper;

    private IncomingListsZipper(final VcsCommittedListsZipper vcsZipper) {
      super(null);
      myVcsZipper = vcsZipper;
    }

    @Override
    public @NotNull Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(@NotNull List<? extends RepositoryLocation> in) {
      return myVcsZipper.groupLocations(in);
    }

    @Override
    public @NotNull CommittedChangeList zip(@NotNull RepositoryLocationGroup group, @NotNull List<? extends CommittedChangeList> lists) {
      if (lists.size() == 1) {
        return lists.get(0);
      }
      final CommittedChangeList victim = ReceivedChangeList.unwrap(lists.get(0));
      final ReceivedChangeList result = new ReceivedChangeList(victim);
      result.setForcePartial(false);
      final Set<Change> baseChanges = new HashSet<>();

      for (CommittedChangeList list : lists) {
        baseChanges.addAll(ReceivedChangeList.unwrap(list).getChanges());

        final Collection<Change> changes = list.getChanges();
        for (Change change : changes) {
          if (! result.getChanges().contains(change)) {
            result.addChange(change);
          }
        }
      }
      result.setForcePartial(baseChanges.size() != result.getChanges().size());
      return result;
    }

    @Override
    public long getNumber(@NotNull CommittedChangeList list) {
      return myVcsZipper.getNumber(list);
    }
  }

  public void commitMessageChanged(@NotNull RepositoryLocation location, long number, String newMessage) {
    myTaskQueue.run(() -> {
      final ChangesCacheFile file = myCachesHolder.haveCache(location);
      if (file != null) {
        try {
          if (file.isEmpty()) return;
          file.editChangelist(number, newMessage);
          loadIncomingChanges(true);
          fireChangesLoaded(location, Collections.emptyList());
        }
        catch (IOException e) {
          VcsBalloonProblemNotifier
            .showOverChangesView(myProject, VcsBundle.message("notification.content.didn.t.update.repository.changes", e.getMessage()),
                                 MessageType.ERROR);
        }
      }
    });
  }

  public void loadIncomingChangesAsync(final @Nullable Consumer<? super List<CommittedChangeList>> consumer, final boolean inBackground) {
    debug("Loading incoming changes");
    final Runnable task = () -> {
      final List<CommittedChangeList> list = loadIncomingChanges(inBackground);
      if (consumer != null) {
        consumer.consume(new ArrayList<>(list));
      }
    };
    myTaskQueue.run(task);
  }

  public void clearCaches(@Nullable Runnable continuation) {
    myTaskQueue.run(() -> {
      myCachesHolder.clearAllCaches();
      myCachedIncomingChangeLists = null;
      if (continuation != null) continuation.run();
      invokeLaterIfNeededOnSyncPublisher(myProject, COMMITTED_TOPIC, listener -> listener.changesCleared());
    });
  }

  public @Nullable List<CommittedChangeList> getCachedIncomingChanges() {
    return myCachedIncomingChangeLists;
  }

  public void processUpdatedFiles(final UpdatedFiles updatedFiles) {
    processUpdatedFiles(updatedFiles, null);
  }

  public void processUpdatedFiles(final UpdatedFiles updatedFiles,
                                  final @Nullable Consumer<? super List<CommittedChangeList>> incomingChangesConsumer) {
    final Runnable task = () -> {
      debug("Processing updated files");
      final Collection<ChangesCacheFile> caches = myCachesHolder.getAllCaches();
      myPendingUpdateCount += caches.size();
      for(final ChangesCacheFile cache: caches) {
        try {
          if (cache.isEmpty()) {
            pendingUpdateProcessed(incomingChangesConsumer);
            continue;
          }
          debug("Processing updated files in " + cache.getLocation());
          boolean needRefresh = cache.processUpdatedFiles(updatedFiles, myNewIncomingChanges);
          if (needRefresh) {
            debug("Found unaccounted files, requesting refresh");
            // todo do we need double-queueing here???
            processUpdatedFilesAfterRefresh(cache, updatedFiles, incomingChangesConsumer);
          }
          else {
            debug("Clearing cached incoming changelists");
            myCachedIncomingChangeLists = null;
            pendingUpdateProcessed(incomingChangesConsumer);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    };
    myTaskQueue.run(task);
  }

  private void pendingUpdateProcessed(@Nullable Consumer<? super List<CommittedChangeList>> incomingChangesConsumer) {
    myPendingUpdateCount--;
    if (myPendingUpdateCount == 0) {
      fireIncomingChangesUpdated(myNewIncomingChanges);
      if (incomingChangesConsumer != null) {
        incomingChangesConsumer.consume(new ArrayList<>(myNewIncomingChanges));
      }
      myNewIncomingChanges.clear();
    }
  }

  private void processUpdatedFilesAfterRefresh(final ChangesCacheFile cache,
                                               final UpdatedFiles updatedFiles,
                                               final @Nullable Consumer<? super List<CommittedChangeList>> incomingChangesConsumer) {
    refreshCacheAsync(cache, false, new RefreshResultConsumer() {
      @Override
      public void receivedChanges(final List<CommittedChangeList> committedChangeLists) {
        try {
          debug("Processing updated files after refresh in " + cache.getLocation());
          boolean result = true;
          if (committedChangeLists.size() > 0) {
            // received some new changelists, try to process updated files again
            result = cache.processUpdatedFiles(updatedFiles, myNewIncomingChanges);
          }
          debug(result ? "Still have unaccounted files" : "No more unaccounted files");
          // for svn, we won't get exact revision numbers in updatedFiles, so we have to double-check by
          // checking revisions we have locally
          if (result) {
            cache.refreshIncomingChanges();
            debug("Clearing cached incoming changelists");
            myCachedIncomingChangeLists = null;
          }
          pendingUpdateProcessed(incomingChangesConsumer);
        }
        catch (IOException e) {
          LOG.error(e);
        }
        catch(VcsException e) {
          notifyRefreshError(e);
        }
      }

      @Override
      public void receivedError(VcsException ex) {
        notifyRefreshError(ex);
      }
    });
  }

  private void fireIncomingChangesUpdated(@NotNull Collection<? extends CommittedChangeList> incomingChanges) {
    List<CommittedChangeList> incomingChangesCopy = unmodifiableOrEmptyList(new ArrayList<>(incomingChanges));

    invokeLaterIfNeededOnSyncPublisher(myProject, COMMITTED_TOPIC, listener -> listener.incomingChangesUpdated(incomingChangesCopy));
  }

  private void notifyRefreshError(final VcsException e) {
    invokeLaterIfNeededOnSyncPublisher(myProject, COMMITTED_TOPIC, listener -> listener.refreshErrorStatusChanged(e));
  }


  public boolean isRefreshingIncomingChanges() {
    return myRefreshingIncomingChanges;
  }

  public boolean refreshIncomingChanges() {
    boolean hasChanges = false;
    final Collection<ChangesCacheFile> caches = myCachesHolder.getAllCaches();
    for(ChangesCacheFile file: caches) {
      try {
        if (file.isEmpty()) {
          continue;
        }
        debug("Refreshing incoming changes for " + file.getLocation());
        boolean changesForCache = file.refreshIncomingChanges();
        hasChanges |= changesForCache;
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch(VcsException e) {
        notifyRefreshError(e);
      }
    }
    return hasChanges;
  }

  public void refreshIncomingChangesAsync() {
    debug("Refreshing incoming changes in background");
    myRefreshingIncomingChanges = true;
    myTaskQueue.run(() -> {
      refreshIncomingChanges();
      refreshIncomingUi();
    });
  }

  private void refreshIncomingUi() {
    ApplicationManager.getApplication().invokeLater(() -> {
      myRefreshingIncomingChanges = false;
      debug("Incoming changes refresh complete, clearing cached incoming changes");
      notifyReloadIncomingChanges();
    }, ModalityState.nonModal(), myProject.getDisposed());
  }

  public void refreshAllCachesAsync(final boolean initIfEmpty, final boolean inBackground) {
    final Runnable task = () -> {
      Collection<ChangesCacheFile> files = myCachesHolder.getAllCaches();
      final RefreshResultConsumer notifyConsumer = new RefreshResultConsumer() {
        private VcsException myError = null;
        private int myCount = 0;
        private int totalChangesCount = 0;

        @Override
        public void receivedChanges(List<CommittedChangeList> changes) {
          totalChangesCount += changes.size();
          checkDone();
        }

        @Override
        public void receivedError(VcsException ex) {
          myError = ex;
          checkDone();
        }

        private void checkDone() {
          myCount++;
          if (myCount == files.size()) {
            myTaskQueue.run(() -> {
              if (totalChangesCount > 0) {
                notifyReloadIncomingChanges();
              } else {
                myProject.getMessageBus().syncPublisher(CommittedChangesTreeBrowser.ITEMS_RELOADED).emptyRefresh();
              }
            });
            notifyRefreshError(myError);
          }
        }
      };
      for(ChangesCacheFile file: files) {
        if ((! inBackground) || file.getVcs().isVcsBackgroundOperationsAllowed(file.getRootPath().getVirtualFile())) {
          refreshCacheAsync(file, initIfEmpty, notifyConsumer, false);
        }
      }
    };
    myTaskQueue.run(task);
  }

  private void notifyReloadIncomingChanges() {
    myCachedIncomingChangeLists = null;

    Runnable runnable = () -> loadIncomingChanges(true);

    if (ApplicationManager.getApplication().isDispatchThread()) {
      myTaskQueue.run(runnable);
    }
    else {
      runnable.run();
    }
  }

  private void refreshCacheAsync(final ChangesCacheFile cache, final boolean initIfEmpty,
                                 final @Nullable RefreshResultConsumer consumer) {
    refreshCacheAsync(cache, initIfEmpty, consumer, true);
  }

  private void refreshCacheAsync(final ChangesCacheFile cache, final boolean initIfEmpty,
                                 final @Nullable RefreshResultConsumer consumer, final boolean asynch) {
    try {
      if (!initIfEmpty && cache.isEmpty()) {
        return;
      }
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    final Runnable task = () -> {
      try {
        final List<CommittedChangeList> list;
        if (initIfEmpty && cache.isEmpty()) {
          list = initCache(cache);
        }
        else {
          list = refreshCache(cache);
        }
        if (consumer != null) {
          consumer.receivedChanges(list);
        }
      }
      catch(ProcessCanceledException ex) {
        // ignore
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (VcsException e) {
        if (consumer != null) {
          consumer.receivedError(e);
        }
      }
    };
    if (asynch) {
      myTaskQueue.run(task);
    } else {
      task.run();
    }
  }

  private void updateRefreshTimer() {
    cancelRefreshTimer();
    if (getState().isRefreshEnabled()) {
      refreshRunnable = new MyRefreshRunnable(this);
      // if "schedule with fixed rate" is used, then after waking up from stand-by mode, events are generated for inactive period
      // it does not make sense
      myFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(refreshRunnable,
                                                                    getState().getRefreshInterval() * 60L,
                                                                    getState().getRefreshInterval() * 60L,
                                                                    TimeUnit.SECONDS);
    }
  }

  private void cancelRefreshTimer() {
    if (refreshRunnable != null) {
      refreshRunnable.cancel();
      refreshRunnable = null;
    }
    if (myFuture != null) {
      myFuture.cancel(false);
      myFuture = null;
    }
  }

  public @Nullable Pair<CommittedChangeList, Change> getIncomingChangeList(final VirtualFile file) {
    if (myCachedIncomingChangeLists != null) {
      File ioFile = new File(file.getPath());
      for(CommittedChangeList changeList: myCachedIncomingChangeLists) {
        for(Change change: changeList.getChanges()) {
          if (change.affectsFile(ioFile)) {
            return Pair.create(changeList, change);
          }
        }
      }
    }
    return null;
  }

  private static long getLatestListForFile(final ChangesCacheFile file) {
    try {
      if ((file == null) || (file.isEmpty())) {
        return -1;
      }
      return file.getLastCachedChangelist();
    }
    catch (IOException e) {
      return -1;
    }
  }

  @ApiStatus.Internal
  public CachesHolder getCachesHolder() {
    return myCachesHolder;
  }

  public void submitExternallyLoaded(final RepositoryLocation location, final long myLastCl, final List<CommittedChangeList> lists) {
    myExternallyLoadedChangeLists.put(location.getKey(), new Pair<>(myLastCl, lists));
  }

  private interface RefreshResultConsumer {
    void receivedChanges(List<CommittedChangeList> changes);
    void receivedError(VcsException ex);
  }

  private static final class MyRefreshRunnable implements Runnable {
    private CommittedChangesCache myCache;

    private MyRefreshRunnable(final CommittedChangesCache cache) {
      myCache = cache;
    }

    private void cancel() {
      myCache = null;
    }

    @Override
    public void run() {
      final CommittedChangesCache cache = myCache;
      if (cache == null) return;
      cache.refreshAllCachesAsync(false, true);
      for(ChangesCacheFile file: cache.getCachesHolder().getAllCaches()) {
        if (file.getVcs().isVcsBackgroundOperationsAllowed(file.getRootPath().getVirtualFile())) {
          if (file.getProvider().refreshIncomingWithCommitted()) {
            cache.refreshIncomingChangesAsync();
            break;
          }
        }
      }
    }
  }

  public RepositoryLocationCache getLocationCache() {
    return myLocationCache;
  }
}
