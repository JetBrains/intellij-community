/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.actions.ChangeListRemoveConfirmation;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictTracker;
import com.intellij.openapi.vcs.changes.ui.CommitHelper;
import com.intellij.openapi.vcs.changes.ui.PlusMinusModify;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.impl.*;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED;

@State(name = "ChangeListManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ChangeListManagerImpl extends ChangeListManagerEx implements ProjectComponent, ChangeListOwner, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListManagerImpl");
  private static final String EXCLUDED_CONVERTED_TO_IGNORED_OPTION = "EXCLUDED_CONVERTED_TO_IGNORED";

  public static final Topic<LocalChangeListsLoadedListener> LISTS_LOADED =
    new Topic<>("LOCAL_CHANGE_LISTS_LOADED", LocalChangeListsLoadedListener.class);

  private final Project myProject;
  private final VcsConfiguration myConfig;
  private final ChangesViewI myChangesViewManager;
  private final FileStatusManager myFileStatusManager;
  private final ChangelistConflictTracker myConflictTracker;
  private VcsDirtyScopeManager myDirtyScopeManager;

  private final Scheduler myScheduler = new Scheduler(); // update thread

  private final EventDispatcher<ChangeListListener> myListeners = EventDispatcher.create(ChangeListListener.class);
  private final DelayedNotificator myDelayedNotificator; // notifies myListeners on the update thread

  private final Object myDataLock = new Object();

  private final IgnoredFilesComponent myIgnoredIdeaLevel;
  private final UpdateRequestsQueue myUpdater;
  private final Modifier myModifier;

  private FileHolderComposite myComposite;
  private ChangeListWorker myWorker;

  private VcsException myUpdateException;
  private Factory<JComponent> myAdditionalInfo;
  private boolean myShowLocalChangesInvalidated;

  @NotNull private ProgressIndicator myUpdateChangesProgressIndicator = createProgressIndicator();
  private volatile String myFreezeName;

  @NotNull private final Collection<LocalChangeList> myListsToBeDeleted = new HashSet<>();
  private boolean myModalNotificationsBlocked;

  private final List<CommitExecutor> myRegisteredCommitExecutors = new ArrayList<>();

  private boolean myExcludedConvertedToIgnored;

  public static ChangeListManagerImpl getInstanceImpl(final Project project) {
    return (ChangeListManagerImpl)getInstance(project);
  }

  void setDirtyScopeManager(VcsDirtyScopeManager dirtyScopeManager) {
    myDirtyScopeManager = dirtyScopeManager;
  }

  public ChangeListManagerImpl(@NotNull Project project, VcsConfiguration config) {
    myProject = project;
    myConfig = config;
    myChangesViewManager = myProject.isDefault() ? new DummyChangesView(myProject) : ChangesViewManager.getInstance(myProject);
    myFileStatusManager = FileStatusManager.getInstance(myProject);
    myConflictTracker = new ChangelistConflictTracker(project, this, myFileStatusManager, EditorNotifications.getInstance(project));

    myIgnoredIdeaLevel = new IgnoredFilesComponent(myProject, true);

    myComposite = new FileHolderComposite(project);
    myWorker = new ChangeListWorker(myProject, new MyChangesDeltaForwarder(myProject, myScheduler));
    myDelayedNotificator = new DelayedNotificator(myListeners, myScheduler);

    myUpdater = new UpdateRequestsQueue(myProject, myScheduler, () -> updateImmediately());
    myModifier = new Modifier(myWorker, myDelayedNotificator);

    myListeners.addListener(new ChangeListAdapter() {
      @Override
      public void defaultListChanged(final ChangeList oldDefaultList, ChangeList newDefaultList) {
        final LocalChangeList oldList = (LocalChangeList)oldDefaultList;
        if (oldDefaultList == null || oldList.hasDefaultName() || oldDefaultList.equals(newDefaultList)) return;

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          scheduleAutomaticEmptyChangeListDeletion(oldList);
        }
      }
    });

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
        @Override
        public void projectClosing(Project project) {
          //noinspection TestOnlyProblems
          waitEverythingDoneInTestMode();
        }
      });
    }
  }

  @Override
  public void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList oldList) {
    if (oldList.isReadOnly() || !oldList.getChanges().isEmpty()) return;

    invokeAfterUpdate(() -> {
      LocalChangeList actualList = getChangeList(oldList.getId());
      if (actualList == null || actualList.isDefault() || !actualList.getChanges().isEmpty()) {
        return;
      }

      if (myModalNotificationsBlocked &&
          myConfig.REMOVE_EMPTY_INACTIVE_CHANGELISTS == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION) {
        myListsToBeDeleted.add(oldList);
      } else {
        deleteEmptyChangeLists(Collections.singletonList(actualList));
      }
    }, InvokeAfterUpdateMode.SILENT, null, null);
  }

  private void deleteEmptyChangeLists(@NotNull Collection<LocalChangeList> lists) {
    if (lists.isEmpty() || myConfig.REMOVE_EMPTY_INACTIVE_CHANGELISTS == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      return;
    }

    ChangeListRemoveConfirmation.processLists(myProject, false, lists, new ChangeListRemoveConfirmation() {
      @Override
      public boolean askIfShouldRemoveChangeLists(@NotNull List<? extends LocalChangeList> toAsk) {
        return myConfig.REMOVE_EMPTY_INACTIVE_CHANGELISTS == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY ||
               showRemoveEmptyChangeListsProposal(myProject, myConfig, toAsk);
      }
    });
  }

  /**
   * Shows the proposal to delete one or more changelists that were default and became empty.
   *
   * @return true if the changelists have to be deleted, false if not.
   */
  public static boolean showRemoveEmptyChangeListsProposal(@NotNull Project project,
                                                           @NotNull final VcsConfiguration config,
                                                           @NotNull Collection<? extends ChangeList> lists) {
    if (lists.isEmpty()) {
      return false;
    }

    final String question;
    if (lists.size() == 1) {
      question = String.format("<html>The empty changelist '%s' is no longer active.<br>Do you want to remove it?</html>",
                               StringUtil.first(lists.iterator().next().getName(), 30, true));
    }
    else {
      question = String.format("<html>Empty changelists<br/>%s are no longer active.<br>Do you want to remove them?</html>",
                               StringUtil.join(lists, list -> StringUtil.first(list.getName(), 30, true), "<br/>"));
    }

    VcsConfirmationDialog dialog = new VcsConfirmationDialog(project, "Remove Empty Changelist", "Remove", "Cancel", new VcsShowConfirmationOption() {
      @Override
      public Value getValue() {
        return config.REMOVE_EMPTY_INACTIVE_CHANGELISTS;
      }

      @Override
      public void setValue(Value value) {
        config.REMOVE_EMPTY_INACTIVE_CHANGELISTS = value;
      }

      @Override
      public boolean isPersistent() {
        return true;
      }
    }, question, "&Remember my choice");
    return dialog.showAndGet();
  }

  @Override
  @CalledInAwt
  public void blockModalNotifications() {
    myModalNotificationsBlocked = true;
  }

  @Override
  @CalledInAwt
  public void unblockModalNotifications() {
    myModalNotificationsBlocked = false;
    deleteEmptyChangeLists(myListsToBeDeleted);
    myListsToBeDeleted.clear();
  }

  @Override
  public void projectOpened() {
    initializeForNewProject();

    VcsListener vcsListener = new VcsListener() {
      @Override
      public void directoryMappingChanged() {
        VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      }
    };

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myUpdater.initialized();
      myProject.getMessageBus().connect().subscribe(VCS_CONFIGURATION_CHANGED, vcsListener);
    }
    else {
      ((ProjectLevelVcsManagerImpl)vcsManager).addInitializationRequest(
        VcsInitObject.CHANGE_LIST_MANAGER, (DumbAwareRunnable)() -> {
          myUpdater.initialized();
          broadcastStateAfterLoad();
          myProject.getMessageBus().connect().subscribe(VCS_CONFIGURATION_CHANGED, vcsListener);
        });

      myConflictTracker.startTracking();
    }
  }

  private void broadcastStateAfterLoad() {
    List<LocalChangeList> listCopy = getChangeListsCopy();
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(LISTS_LOADED).processLoadedLists(listCopy);
    }
  }

  @CalledInAwt
  private void initializeForNewProject() {
    synchronized (myDataLock) {
      if (!Registry.is("ide.hide.excluded.files") && !myExcludedConvertedToIgnored) {
        convertExcludedToIgnored();
        myExcludedConvertedToIgnored = true;
      }
    }
  }

  void convertExcludedToIgnored() {
    for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(myProject)) {
      for (VirtualFile file : policy.getExcludeRootsForProject()) {
        addDirectoryToIgnoreImplicitly(file.getPath());
      }
    }

    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      for (String url : ModuleRootManager.getInstance(module).getExcludeRootUrls()) {
        VirtualFile file = virtualFileManager.findFileByUrl(url);
        if (file != null && !fileIndex.isExcluded(file)) {
          //root is included into some inner module so it shouldn't be ignored
          continue;
        }
        addDirectoryToIgnoreImplicitly(VfsUtilCore.urlToPath(url));
      }
    }
  }

  @Override
  public void projectClosed() {
    synchronized (myDataLock) {
      myUpdateChangesProgressIndicator.cancel();
    }

    myUpdater.stop();
    myConflictTracker.stopTracking();
  }

  @Override
  @NotNull @NonNls
  public String getComponentName() {
    return "ChangeListManager";
  }

  /**
   * update itself might produce actions done on AWT thread (invoked-after),
   * so waiting for its completion on AWT thread is not good runnable is invoked on AWT thread
   */
  @Override
  public void invokeAfterUpdate(@NotNull Runnable afterUpdate,
                                @NotNull InvokeAfterUpdateMode mode,
                                @Nullable String title,
                                @Nullable ModalityState state) {
    myUpdater.invokeAfterUpdate(afterUpdate, mode, title, null, state);
  }

  @Override
  public void invokeAfterUpdate(@NotNull Runnable afterUpdate,
                                @NotNull InvokeAfterUpdateMode mode,
                                @Nullable String title,
                                @Nullable Consumer<VcsDirtyScopeManager> dirtyScopeManagerFiller,
                                @Nullable ModalityState state) {
    myUpdater.invokeAfterUpdate(afterUpdate, mode, title, dirtyScopeManagerFiller, state);
  }

  @Override
  public void freeze(@NotNull String reason) {
    myUpdater.setIgnoreBackgroundOperation(true);
    Semaphore sem = new Semaphore();
    sem.down();

    invokeAfterUpdate(() -> {
      myUpdater.setIgnoreBackgroundOperation(false);
      myUpdater.pause();
      myFreezeName = reason;
      sem.up();
    }, InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED, "", ModalityState.defaultModalityState());

    boolean free = false;
    while (!free) {
      ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
      if (pi != null) pi.checkCanceled();
      free = sem.waitFor(500);
    }
  }

  @Override
  public void unfreeze() {
    myUpdater.go();
    myFreezeName = null;
  }

  @Override
  public String isFreezed() {
    return myFreezeName;
  }

  public void executeOnUpdaterThread(@NotNull Runnable r) {
    myScheduler.submit(r);
  }

  @Override
  public void scheduleUpdate() {
    myUpdater.schedule();
  }

  @Override
  public void scheduleUpdate(boolean updateUnversionedFiles) {
    myUpdater.schedule();
  }

  private void filterOutIgnoredFiles(final List<VcsDirtyScope> scopes) {
    final Set<VirtualFile> refreshFiles = new HashSet<>();
    try {
      synchronized (myDataLock) {
        final IgnoredFilesCompositeHolder fileHolder = myComposite.getIgnoredFileHolder();

        for (Iterator<VcsDirtyScope> iterator = scopes.iterator(); iterator.hasNext(); ) {
          final VcsModifiableDirtyScope scope = (VcsModifiableDirtyScope)iterator.next();
          final VcsDirtyScopeModifier modifier = scope.getModifier();
          if (modifier == null) continue;

          fileHolder.notifyVcsStarted(scope.getVcs());

          filterOutIgnoredFiles(modifier.getDirtyFilesIterator(), fileHolder, refreshFiles);

          for (VirtualFile root : modifier.getAffectedVcsRoots()) {
            filterOutIgnoredFiles(modifier.getDirtyDirectoriesIterator(root), fileHolder, refreshFiles);
          }

          modifier.recheckDirtyKeys();

          if (scope.isEmpty()) {
            iterator.remove();
          }
        }
      }
    }
    catch (Exception | AssertionError ex) {
      LOG.error(ex);
    }
    for (VirtualFile file : refreshFiles) {
      myFileStatusManager.fileStatusChanged(file);
    }
  }

  private void filterOutIgnoredFiles(Iterator<FilePath> iterator,
                                     IgnoredFilesCompositeHolder fileHolder,
                                     Set<VirtualFile> refreshFiles) {
    while (iterator.hasNext()) {
      VirtualFile file = iterator.next().getVirtualFile();
      if (file != null && isIgnoredFile(file)) {
        iterator.remove();
        fileHolder.addFile(file);
        refreshFiles.add(file);
      }
    }
  }

  private void updateImmediately() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (!vcsManager.hasActiveVcss()) return;

    final VcsInvalidated invalidated = myDirtyScopeManager.retrieveScopes();
    if (checkScopeIsEmpty(invalidated)) {
      myDirtyScopeManager.changesProcessed();
      return;
    }

    final boolean wasEverythingDirty = invalidated.isEverythingDirty();
    final List<VcsDirtyScope> scopes = invalidated.getScopes();

    try {
      if (myUpdater.isStopped()) return;

      // copy existing data to objects that would be updated.
      // mark for "modifier" that update started (it would create duplicates of modification commands done by user during update;
      // after update of copies of objects is complete, it would apply the same modifications to copies.)
      final DataHolder dataHolder;
      ProgressIndicator indicator = createProgressIndicator();
      synchronized (myDataLock) {
        dataHolder = new DataHolder(myComposite.copy(), myWorker.copy(), wasEverythingDirty);
        myModifier.enterUpdate();
        if (wasEverythingDirty) {
          myUpdateException = null;
          myAdditionalInfo = null;
        }
        myUpdateChangesProgressIndicator = indicator;

        if (LOG.isDebugEnabled()) {
          String scopeInString = StringUtil.join(scopes, scope -> scope.toString(), "->\n");
          LOG.debug("refresh procedure started, everything: " + wasEverythingDirty + " dirty scope: " + scopeInString +
                    "\ncurrent changes: " + myWorker);
        }
      }
      dataHolder.notifyStart();
      myChangesViewManager.scheduleRefresh();

      ProgressManager.getInstance().runProcess(() -> {
        iterateScopes(dataHolder, scopes, wasEverythingDirty, indicator);
      }, indicator);

      boolean takeChanges;
      synchronized (myDataLock) {
        takeChanges = myUpdateException == null;
      }
      if (takeChanges) {
        // update IDEA-level ignored files
        updateIgnoredFiles(dataHolder.getComposite());
      }

      clearCurrentRevisionsCache(invalidated);
      // for the case of project being closed we need a read action here -> to be more consistent
      ApplicationManager.getApplication().runReadAction(() -> {
        if (myProject.isDisposed()) {
          return;
        }
        synchronized (myDataLock) {
          // do same modifications to change lists as was done during update + do delayed notifications
          dataHolder.notifyEnd();
          // update member from copy
          if (takeChanges) {
            final ChangeListWorker oldWorker = myWorker;
            myWorker = dataHolder.getChangeListWorker();
            myWorker.onAfterWorkerSwitch(oldWorker);
            myModifier.finishUpdate(myWorker);

            if (LOG.isDebugEnabled()) {
              LOG.debug("refresh procedure finished, unversioned size: " +
                        dataHolder.getComposite().getVFHolder(FileHolder.HolderType.UNVERSIONED).getFiles().size() + "\nchanges: " + myWorker);
            }
            final boolean statusChanged = !myComposite.equals(dataHolder.getComposite());
            myComposite = dataHolder.getComposite();
            if (statusChanged) {
              myDelayedNotificator.unchangedFileStatusChanged();
            }
          }
          else {
            myModifier.finishUpdate(null);
          }
          myShowLocalChangesInvalidated = false;
        }
      });

      for (VcsDirtyScope scope : scopes) {
        AbstractVcs vcs = scope.getVcs();
        if (vcs != null && vcs.isTrackingUnchangedContent()) {
          scope.iterateExistingInsideScope(file -> {
            LastUnchangedContentTracker.markUntouched(file); //todo what if it has become dirty again during update?
            return true;
          });
        }
      }


      myChangesViewManager.scheduleRefresh();
    }
    catch (ProcessCanceledException e) {
      // OK, we're finishing all the stuff now.
    }
    catch (Exception | AssertionError ex) {
      LOG.error(ex);
    }
    finally {
      myDirtyScopeManager.changesProcessed();

      synchronized (myDataLock) {
        myDelayedNotificator.changeListUpdateDone();
        myChangesViewManager.scheduleRefresh();
      }
    }
  }

  private boolean checkScopeIsEmpty(VcsInvalidated invalidated) {
    if (invalidated == null) return true;
    if (invalidated.isEverythingDirty()) return false;
    if (invalidated.isEmpty()) return true;

    filterOutIgnoredFiles(invalidated.getScopes());
    return invalidated.isEmpty();
  }

  private void iterateScopes(DataHolder dataHolder,
                             List<VcsDirtyScope> scopes,
                             boolean wasEverythingDirty,
                             @NotNull ProgressIndicator indicator) {
    final ChangeListManagerGate gate = dataHolder.getChangeListWorker().createGate();
    // do actual requests about file statuses
    Getter<Boolean> disposedGetter = () -> myProject.isDisposed() || myUpdater.isStopped();
    final UpdatingChangeListBuilder builder = new UpdatingChangeListBuilder(dataHolder.getChangeListWorker(),
                                                                            dataHolder.getComposite(), disposedGetter, this,
                                                                            gate);

    for (final VcsDirtyScope scope : scopes) {
      indicator.checkCanceled();

      final AbstractVcs vcs = scope.getVcs();
      if (vcs == null) continue;
      scope.setWasEverythingDirty(wasEverythingDirty);

      myChangesViewManager.setBusy(true);

      actualUpdate(builder, scope, vcs, dataHolder, gate, indicator);

      synchronized (myDataLock) {
        if (myUpdateException != null) break;
      }
    }
    synchronized (myDataLock) {
      if (myAdditionalInfo == null) {
        myAdditionalInfo = builder.getAdditionalInfo();
      }
    }
  }

  private void clearCurrentRevisionsCache(final VcsInvalidated invalidated) {
    final ContentRevisionCache cache = ProjectLevelVcsManager.getInstance(myProject).getContentRevisionCache();
    if (invalidated.isEverythingDirty()) {
      cache.clearAllCurrent();
    }
    else {
      cache.clearScope(invalidated.getScopes());
    }
  }

  @NotNull
  private static ProgressIndicator createProgressIndicator() {
    return new EmptyProgressIndicator();
  }

  private class DataHolder {
    private final boolean myWasEverythingDirty;
    private final FileHolderComposite myComposite;
    private final ChangeListWorker myChangeListWorker;

    private DataHolder(FileHolderComposite composite, ChangeListWorker changeListWorker, boolean wasEverythingDirty) {
      myComposite = composite;
      myChangeListWorker = changeListWorker;
      myWasEverythingDirty = wasEverythingDirty;
    }

    private void notifyStart() {
      if (myWasEverythingDirty) {
        myComposite.cleanAll();
        myChangeListWorker.notifyStartProcessingChanges(null);
      }
    }

    private void notifyStartProcessingChanges(@NotNull final VcsModifiableDirtyScope scope) {
      if (!myWasEverythingDirty) {
        myComposite.cleanAndAdjustScope(scope);
        myChangeListWorker.notifyStartProcessingChanges(scope);
      }

      myComposite.notifyVcsStarted(scope.getVcs());
    }

    private void notifyDoneProcessingChanges() {
      if (!myWasEverythingDirty) {
        myChangeListWorker.notifyDoneProcessingChanges(myDelayedNotificator);
      }
    }

    void notifyEnd() {
      if (myWasEverythingDirty) {
        myChangeListWorker.notifyDoneProcessingChanges(myDelayedNotificator);
      }
    }

    public FileHolderComposite getComposite() {
      return myComposite;
    }

    ChangeListWorker getChangeListWorker() {
      return myChangeListWorker;
    }
  }

  private void actualUpdate(@NotNull UpdatingChangeListBuilder builder,
                            @NotNull VcsDirtyScope scope,
                            @NotNull AbstractVcs vcs,
                            @NotNull DataHolder dataHolder,
                            @NotNull ChangeListManagerGate gate,
                            @NotNull ProgressIndicator indicator) {
    dataHolder.notifyStartProcessingChanges((VcsModifiableDirtyScope)scope);
    try {
      final ChangeProvider changeProvider = vcs.getChangeProvider();
      if (changeProvider != null) {
        builder.setCurrent(scope);
        changeProvider.getChanges(scope, builder, indicator, gate);
      }
    }
    catch (VcsException e) {
      handleUpdateException(e);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable t) {
      LOG.debug(t);
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
    finally {
      if (!myUpdater.isStopped()) {
        dataHolder.notifyDoneProcessingChanges();
      }
    }
  }

  private void handleUpdateException(final VcsException e) {
    LOG.info(e);

    if (e instanceof VcsConnectionProblem) {
      ApplicationManager.getApplication().invokeLater(() -> ((VcsConnectionProblem)e).attemptQuickFix(false));
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
      if (helper instanceof AbstractVcsHelperImpl && ((AbstractVcsHelperImpl)helper).handleCustom(e)) {
        return;
      }
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }

    synchronized (myDataLock) {
      myUpdateException = e;
    }
  }

  public static boolean isUnder(final Change change, final VcsDirtyScope scope) {
    final ContentRevision before = change.getBeforeRevision();
    final ContentRevision after = change.getAfterRevision();
    return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
  }

  @NotNull
  @Override
  public List<LocalChangeList> getChangeListsCopy() {
    synchronized (myDataLock) {
      return ContainerUtil.map(myWorker.getChangeLists(), LocalChangeList::copy);
    }
  }

  @Override
  @NotNull
  public List<LocalChangeList> getChangeLists() {
    synchronized (myDataLock) {
      return getChangeListsCopy();
    }
  }

  @NotNull
  @Override
  public List<File> getAffectedPaths() {
    synchronized (myDataLock) {
      return myWorker.getAffectedPaths();
    }
  }

  @Override
  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    synchronized (myDataLock) {
      return myWorker.getAffectedFiles();
    }
  }

  @Override
  @NotNull
  public Collection<Change> getAllChanges() {
    synchronized (myDataLock) {
      return myWorker.getAllChanges();
    }
  }

  @NotNull
  public List<VirtualFile> getUnversionedFiles() {
    synchronized (myDataLock) {
      return myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).getFiles();
    }
  }

  @NotNull
  @Override
  public List<VirtualFile> getModifiedWithoutEditing() {
    synchronized (myDataLock) {
      return myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).getFiles();
    }
  }

  /**
   * @return only roots for ignored folders, and ignored files
   */
  @NotNull
  public List<VirtualFile> getIgnoredFiles() {
    synchronized (myDataLock) {
      return new ArrayList<>(myComposite.getIgnoredFileHolder().values());
    }
  }

  boolean isIgnoredInUpdateMode() {
    synchronized (myDataLock) {
      return myComposite.getIgnoredFileHolder().isInUpdatingMode();
    }
  }

  public List<VirtualFile> getLockedFolders() {
    synchronized (myDataLock) {
      return myComposite.getVFHolder(FileHolder.HolderType.LOCKED).getFiles();
    }
  }

  Map<VirtualFile, LogicalLock> getLogicallyLockedFolders() {
    synchronized (myDataLock) {
      return new HashMap<>(myComposite.getLogicallyLockedFileHolder().getMap());
    }
  }

  public boolean isLogicallyLocked(final VirtualFile file) {
    synchronized (myDataLock) {
      return myComposite.getLogicallyLockedFileHolder().containsKey(file);
    }
  }

  public boolean isContainedInLocallyDeleted(final FilePath filePath) {
    synchronized (myDataLock) {
      return myComposite.getDeletedFileHolder().isContainedInLocallyDeleted(filePath);
    }
  }

  public List<LocallyDeletedChange> getDeletedFiles() {
    synchronized (myDataLock) {
      return myComposite.getDeletedFileHolder().getFiles();
    }
  }

  MultiMap<String, VirtualFile> getSwitchedFilesMap() {
    synchronized (myDataLock) {
      return myComposite.getSwitchedFileHolder().getBranchToFileMap();
    }
  }

  @Nullable
  Map<VirtualFile, String> getSwitchedRoots() {
    synchronized (myDataLock) {
      return myComposite.getRootSwitchFileHolder().getFilesMapCopy();
    }
  }

  public VcsException getUpdateException() {
    synchronized (myDataLock) {
      return myUpdateException;
    }
  }

  Factory<JComponent> getAdditionalUpdateInfo() {
    synchronized (myDataLock) {
      return myAdditionalInfo;
    }
  }

  @Override
  public boolean isFileAffected(@NotNull VirtualFile file) {
    synchronized (myDataLock) {
      return myWorker.getStatus(file) != null;
    }
  }

  @Override
  @Nullable
  public LocalChangeList findChangeList(final String name) {
    synchronized (myDataLock) {
      return myWorker.getChangeListCopyByName(name);
    }
  }

  @Override
  public LocalChangeList getChangeList(String id) {
    synchronized (myDataLock) {
      LocalChangeList list = myWorker.getChangeListById(id);
      return list != null ? list.copy() : null;
    }
  }

  @Override
  public LocalChangeList addChangeList(@NotNull final String name, @Nullable final String comment) {
    return addChangeList(name, comment, null);
  }

  @NotNull
  @Override
  public LocalChangeList addChangeList(@NotNull final String name, @Nullable final String comment, @Nullable final Object data) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final LocalChangeList changeList = myModifier.addChangeList(name, comment, data);
        myChangesViewManager.scheduleRefresh();
        return changeList;
      }
    });
  }


  @Override
  public void removeChangeList(final String name) {
    ApplicationManager.getApplication().runReadAction(() -> {
      synchronized (myDataLock) {
        myModifier.removeChangeList(name);
        myChangesViewManager.scheduleRefresh();
      }
    });
  }

  @Override
  public void removeChangeList(LocalChangeList list) {
    removeChangeList(list.getName());
  }

  @Override
  public void setDefaultChangeList(@NotNull String name) {
    ApplicationManager.getApplication().runReadAction(() -> {
      synchronized (myDataLock) {
        myModifier.setDefault(name);
      }
    });
    myChangesViewManager.scheduleRefresh();
  }

  @Override
  public void setDefaultChangeList(@NotNull final LocalChangeList list) {
    setDefaultChangeList(list.getName());
  }

  @NotNull
  @Override
  public LocalChangeList getDefaultChangeList() {
    synchronized (myDataLock) {
      return myWorker.getDefaultList().copy();
    }
  }

  @Override
  @NotNull
  public Collection<LocalChangeList> getInvolvedListsFilterChanges(@NotNull Collection<Change> changes, @NotNull List<Change> validChanges) {
    synchronized (myDataLock) {
      Collection<LocalChangeList> changelists = myWorker.getInvolvedListsFilterChanges(changes, validChanges);
      return ContainerUtil.map(changelists, LocalChangeList::copy);
    }
  }

  @Override
  @Nullable
  public LocalChangeList getChangeList(@NotNull Change change) {
    synchronized (myDataLock) {
      LocalChangeList list = myWorker.getChangeListForChange(change);
      return list != null ? list.copy() : null;
    }
  }

  @Override
  public String getChangeListNameIfOnlyOne(final Change[] changes) {
    synchronized (myDataLock) {
      LocalChangeList list = myWorker.getChangeListIfOnlyOne(changes);
      return list != null ? list.getName() : null;
    }
  }

  /**
   * @deprecated better use normal comparison, with equals
   */
  @Override
  @Nullable
  public LocalChangeList getIdentityChangeList(@NotNull Change change) {
    synchronized (myDataLock) {
      final List<LocalChangeList> lists = myWorker.getChangeLists();
      for (LocalChangeList list : lists) {
        for (Change oldChange : list.getChanges()) {
          if (oldChange == change) {
            return list.copy();
          }
        }
      }
      return null;
    }
  }

  @Override
  public boolean isInUpdate() {
    synchronized (myDataLock) {
      return myModifier.isInsideUpdate() || myShowLocalChangesInvalidated;
    }
  }

  @Override
  @Nullable
  public Change getChange(@NotNull VirtualFile file) {
    return getChange(VcsUtil.getFilePath(file));
  }

  @Override
  public LocalChangeList getChangeList(@NotNull VirtualFile file) {
    synchronized (myDataLock) {
      LocalChangeList list = myWorker.getChangeListFor(file);
      return list != null ? list.copy() : null;
    }
  }

  @Override
  @Nullable
  public Change getChange(final FilePath file) {
    synchronized (myDataLock) {
      return myWorker.getChangeForPath(file);
    }
  }

  @Override
  public boolean isUnversioned(VirtualFile file) {
    synchronized (myDataLock) {
      return myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).containsFile(file);
    }
  }

  @Override
  @NotNull
  public FileStatus getStatus(@NotNull VirtualFile file) {
    synchronized (myDataLock) {
      if (myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).containsFile(file)) return FileStatus.UNKNOWN;
      if (myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).containsFile(file)) return FileStatus.HIJACKED;
      if (myComposite.getIgnoredFileHolder().containsFile(file)) return FileStatus.IGNORED;

      final FileStatus status = ObjectUtils.notNull(myWorker.getStatus(file), FileStatus.NOT_CHANGED);

      if (FileStatus.NOT_CHANGED.equals(status)) {
        boolean switched = myComposite.getSwitchedFileHolder().containsFile(file);
        if (switched) return FileStatus.SWITCHED;
      }

      return status;
    }
  }

  @Override
  @NotNull
  public Collection<Change> getChangesIn(@NotNull VirtualFile dir) {
    return getChangesIn(VcsUtil.getFilePath(dir));
  }

  @NotNull
  @Override
  public ThreeState haveChangesUnder(@NotNull final VirtualFile vf) {
    if (!vf.isValid() || !vf.isDirectory()) return ThreeState.NO;
    synchronized (myDataLock) {
      return myWorker.haveChangesUnder(vf);
    }
  }

  @Override
  @NotNull
  public Collection<Change> getChangesIn(@NotNull FilePath dirPath) {
    synchronized (myDataLock) {
      return myWorker.getChangesIn(dirPath);
    }
  }

  @Override
  @Nullable
  public AbstractVcs getVcsFor(@NotNull Change change) {
    VcsKey key;
    synchronized (myDataLock) {
      key = myWorker.getVcsFor(change);
    }
    return key != null ? ProjectLevelVcsManager.getInstance(myProject).findVcsByName(key.getName()) : null;
  }

  @Override
  public void moveChangesTo(final LocalChangeList list, final Change... changes) {
    ApplicationManager.getApplication().runReadAction(() -> {
      synchronized (myDataLock) {
        myModifier.moveChangesTo(list.getName(), changes);
      }
    });
    myChangesViewManager.scheduleRefresh();
  }

  @Override
  public void addUnversionedFiles(final LocalChangeList list, @NotNull final List<VirtualFile> files) {
    addUnversionedFiles(list, files, getDefaultUnversionedFileCondition(), null);
  }

  // TODO this is for quick-fix for GitAdd problem. To be removed after proper fix
  // (which should introduce something like VcsAddRemoveEnvironment)
  @Deprecated
  @NotNull
  public List<VcsException> addUnversionedFiles(final LocalChangeList list,
                                                @NotNull final List<VirtualFile> files,
                                                @NotNull final Condition<FileStatus> statusChecker,
                                                @Nullable Consumer<List<Change>> changesConsumer) {
    final List<VcsException> exceptions = new ArrayList<>();
    final Set<VirtualFile> allProcessedFiles = new HashSet<>();
    ChangesUtil.processVirtualFilesByVcs(myProject, files, (vcs, items) -> {
      final CheckinEnvironment environment = vcs.getCheckinEnvironment();
      if (environment != null) {
        Set<VirtualFile> descendants = getUnversionedDescendantsRecursively(items, statusChecker);
        Set<VirtualFile> parents = getUnversionedParents(vcs, items, statusChecker);

        // it is assumed that not-added parents of files passed to scheduleUnversionedFilesForAddition() will also be added to vcs
        // (inside the method) - so common add logic just needs to refresh statuses of parents
        final List<VcsException> result = ContainerUtil.newArrayList();
        ProgressManager.getInstance().run(new Task.Modal(myProject, "Adding Files to VCS...", true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            indicator.setIndeterminate(true);
            List<VcsException> exs = environment.scheduleUnversionedFilesForAddition(ContainerUtil.newArrayList(descendants));
            if (exs != null) {
              ContainerUtil.addAll(result, exs);
            }
          }
        });

        allProcessedFiles.addAll(descendants);
        allProcessedFiles.addAll(parents);
        exceptions.addAll(result);
      }
    });

    if (!exceptions.isEmpty()) {
      StringBuilder message = new StringBuilder(VcsBundle.message("error.adding.files.prompt"));
      for (VcsException ex : exceptions) {
        message.append("\n").append(ex.getMessage());
      }
      Messages.showErrorDialog(myProject, message.toString(), VcsBundle.message("error.adding.files.title"));
    }

    for (VirtualFile file : allProcessedFiles) {
      myFileStatusManager.fileStatusChanged(file);
    }
    VcsDirtyScopeManager.getInstance(myProject).filesDirty(allProcessedFiles, null);

    final Ref<List<Change>> foundChanges = Ref.create();
    final boolean moveRequired = !list.isDefault();
    boolean syncUpdateRequired = changesConsumer != null;

    if (moveRequired || syncUpdateRequired) {
      // find the changes for the added files and move them to the necessary changelist
      InvokeAfterUpdateMode updateMode =
        syncUpdateRequired ? InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE : InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE;

      invokeAfterUpdate(() -> {
        ApplicationManager.getApplication().runReadAction(() -> {
          synchronized (myDataLock) {
            List<Change> newChanges = ContainerUtil.filter(getDefaultChangeList().getChanges(), change -> {
              FilePath path = ChangesUtil.getAfterPath(change);
              return path != null && allProcessedFiles.contains(path.getVirtualFile());
            });
            foundChanges.set(newChanges);

            if (moveRequired && !newChanges.isEmpty()) {
              moveChangesTo(list, newChanges.toArray(new Change[newChanges.size()]));
            }
          }
        });

        myChangesViewManager.scheduleRefresh();
      }, updateMode, VcsBundle.message("change.lists.manager.add.unversioned"), null);

      if (changesConsumer != null) {
        changesConsumer.consume(foundChanges.get());
      }
    }
    else {
      myChangesViewManager.scheduleRefresh();
    }

    return exceptions;
  }

  @NotNull
  public static Condition<FileStatus> getDefaultUnversionedFileCondition() {
    return status -> status == FileStatus.UNKNOWN;
  }

  @NotNull
  private Set<VirtualFile> getUnversionedDescendantsRecursively(@NotNull List<VirtualFile> items,
                                                                @NotNull final Condition<FileStatus> condition) {
    final Set<VirtualFile> result = ContainerUtil.newHashSet();
    Processor<VirtualFile> addToResultProcessor = file -> {
      if (condition.value(getStatus(file))) {
        result.add(file);
      }
      return true;
    };

    for (VirtualFile item : items) {
      VcsRootIterator.iterateVfUnderVcsRoot(myProject, item, addToResultProcessor);
    }

    return result;
  }

  @NotNull
  private Set<VirtualFile> getUnversionedParents(@NotNull AbstractVcs vcs,
                                                 @NotNull Collection<VirtualFile> items,
                                                 @NotNull Condition<FileStatus> condition) {
    if (!vcs.areDirectoriesVersionedItems()) return Collections.emptySet();

    HashSet<VirtualFile> result = ContainerUtil.newHashSet();

    for (VirtualFile item : items) {
      VirtualFile parent = item.getParent();

      while (parent != null && condition.value(getStatus(parent))) {
        result.add(parent);
        parent = parent.getParent();
      }
    }

    return result;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void addChangeListListener(@NotNull ChangeListListener listener) {
    myListeners.addListener(listener);
  }


  @Override
  public void removeChangeListListener(@NotNull ChangeListListener listener) {
    myListeners.removeListener(listener);
  }

  @Override
  public void registerCommitExecutor(@NotNull CommitExecutor executor) {
    myRegisteredCommitExecutors.add(executor);
  }

  @Override
  public void commitChanges(@NotNull LocalChangeList changeList, @NotNull List<Change> changes) {
    doCommit(changeList, changes, false);
  }

  private boolean doCommit(final LocalChangeList changeList, final List<Change> changes, final boolean synchronously) {
    FileDocumentManager.getInstance().saveAllDocuments();
    return new CommitHelper(myProject, changeList, changes, changeList.getName(),
                            StringUtil.isEmpty(changeList.getComment()) ? changeList.getName() : changeList.getComment(), new ArrayList<>(),
                            false, synchronously, FunctionUtil.nullConstant(), null, false, null).doCommit();
  }

  @TestOnly
  public boolean commitChangesSynchronouslyWithResult(@NotNull LocalChangeList changeList, @NotNull List<Change> changes) {
    return doCommit(changeList, changes, true);
  }

  @Override
  public void loadState(Element element) {
    if (myProject.isDefault()) {
      return;
    }

    synchronized (myDataLock) {
      ChangeListManagerSerialization.readExternal(element, myIgnoredIdeaLevel, myWorker);
    }
    myExcludedConvertedToIgnored = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, EXCLUDED_CONVERTED_TO_IGNORED_OPTION));
    myConflictTracker.loadState(element);
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("state");
    if (myProject.isDefault()) {
      return element;
    }

    final IgnoredFilesComponent ignoredFilesComponent;
    final ChangeListWorker worker;
    synchronized (myDataLock) {
      ignoredFilesComponent = myIgnoredIdeaLevel.copy();
      worker = myWorker.copy();
    }
    ChangeListManagerSerialization.writeExternal(element, ignoredFilesComponent, worker);
    JDOMExternalizerUtil.writeField(element, EXCLUDED_CONVERTED_TO_IGNORED_OPTION, Boolean.toString(myExcludedConvertedToIgnored), Boolean.toString(false));
    myConflictTracker.saveState(element);
    return element;
  }

  // used in TeamCity
  @Override
  public void reopenFiles(@NotNull List<FilePath> paths) {
    final ReadonlyStatusHandlerImpl readonlyStatusHandler = (ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject);
    final boolean savedOption = readonlyStatusHandler.getState().SHOW_DIALOG;
    readonlyStatusHandler.getState().SHOW_DIALOG = false;
    try {
      readonlyStatusHandler.ensureFilesWritable(ContainerUtil.mapNotNull(paths, FilePath::getVirtualFile));
    }
    finally {
      readonlyStatusHandler.getState().SHOW_DIALOG = savedOption;
    }
  }

  @NotNull
  @Override
  public List<CommitExecutor> getRegisteredExecutors() {
    return Collections.unmodifiableList(myRegisteredCommitExecutors);
  }

  @Override
  public void addFilesToIgnore(@NotNull IgnoredFileBean... filesToIgnore) {
    myIgnoredIdeaLevel.add(filesToIgnore);
    scheduleUnversionedUpdate();
  }

  @Override
  public void addDirectoryToIgnoreImplicitly(@NotNull String path) {
    myIgnoredIdeaLevel.addIgnoredDirectoryImplicitly(path, myProject);
  }

  @Override
  public void removeImplicitlyIgnoredDirectory(@NotNull String path) {
    myIgnoredIdeaLevel.removeImplicitlyIgnoredDirectory(path, myProject);
  }

  public IgnoredFilesComponent getIgnoredFilesComponent() {
    return myIgnoredIdeaLevel;
  }

  private void scheduleUnversionedUpdate() {
    Collection<VirtualFile> unversioned;
    Collection<VirtualFile> ignored;
    synchronized (myDataLock) {
      unversioned = myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).getFiles();
      ignored = myComposite.getIgnoredFileHolder().values();
    }

    VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);

    final int ourPiecesLimit = 100;
    if (unversioned.size() + ignored.size() > ourPiecesLimit) {
      vcsDirtyScopeManager.markEverythingDirty();
    }
    else {
      List<VirtualFile> dirs = new ArrayList<>();
      List<VirtualFile> files = new ArrayList<>();

      for (VirtualFile vf : ContainerUtil.concat(unversioned, ignored)) {
        if (vf.isDirectory()) {
          dirs.add(vf);
        }
        else {
          files.add(vf);
        }
      }

      vcsDirtyScopeManager.filesDirty(files, dirs);
    }
  }

  @Override
  public void setFilesToIgnore(@NotNull IgnoredFileBean... filesToIgnore) {
    myIgnoredIdeaLevel.set(filesToIgnore);
    scheduleUnversionedUpdate();
  }

  private void updateIgnoredFiles(final FileHolderComposite composite) {
    final VirtualFileHolder vfHolder = composite.getVFHolder(FileHolder.HolderType.UNVERSIONED);
    final List<VirtualFile> unversionedFiles = vfHolder.getFiles();
    exchangeWithIgnored(composite, vfHolder, unversionedFiles);

    final VirtualFileHolder vfModifiedHolder = composite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING);
    final List<VirtualFile> modifiedFiles = vfModifiedHolder.getFiles();
    exchangeWithIgnored(composite, vfModifiedHolder, modifiedFiles);
  }

  private void exchangeWithIgnored(FileHolderComposite composite, VirtualFileHolder vfHolder, List<VirtualFile> unversionedFiles) {
    for (VirtualFile file : unversionedFiles) {
      if (isIgnoredFile(file)) {
        vfHolder.removeFile(file);
        composite.getIgnoredFileHolder().addFile(file);
      }
    }
  }

  @NotNull
  @Override
  public IgnoredFileBean[] getFilesToIgnore() {
    return myIgnoredIdeaLevel.getFilesToIgnore();
  }

  @Override
  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    FilePath filePath = VcsUtil.getFilePath(file);
    return ContainerUtil.exists(IgnoredFileProvider.IGNORE_FILE.getExtensions(), it -> it.isIgnoredFile(myProject, filePath));
  }

  public static class DefaultIgnoredFileProvider implements IgnoredFileProvider {
    @Override
    public boolean isIgnoredFile(@NotNull Project project, @NotNull FilePath filePath) {
      return getInstanceImpl(project).myIgnoredIdeaLevel.isIgnoredFile(filePath);
    }
  }

  @Override
  @Nullable
  public String getSwitchedBranch(@NotNull VirtualFile file) {
    synchronized (myDataLock) {
      return myComposite.getSwitchedFileHolder().getBranchForFile(file);
    }
  }

  @NotNull
  @Override
  public String getDefaultListName() {
    synchronized (myDataLock) {
      return myWorker.getDefaultList().getName();
    }
  }

  @Override
  public boolean setReadOnly(final String name, final boolean value) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final boolean result = myModifier.setReadOnly(name, value);
        myChangesViewManager.scheduleRefresh();
        return result;
      }
    });
  }

  @Override
  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final boolean result = myModifier.editName(fromName, toName);
        myChangesViewManager.scheduleRefresh();
        return result;
      }
    });
  }

  @Override
  public String editComment(@NotNull final String fromName, final String newComment) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final String oldComment = myModifier.editComment(fromName, newComment);
        myChangesViewManager.scheduleRefresh();
        return oldComment;
      }
    });
  }

  @TestOnly
  public void waitUntilRefreshed() {
    VcsDirtyScopeVfsListener.getInstance(myProject).flushDirt();
    myUpdater.waitUntilRefreshed();
    waitUpdateAlarm();
  }

  @TestOnly
  private void waitUpdateAlarm() {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    myScheduler.submit(() -> semaphore.up());
    semaphore.waitFor();
  }

  @TestOnly
  public void stopEveryThingIfInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    Future future = myScheduler.myLastTask.get();
    if (future != null) {
      future.cancel(true);
      myScheduler.myLastTask.compareAndSet(future, null);
    }
  }

  @TestOnly
  public void waitEverythingDoneInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    while (true) {
      Future future = myScheduler.myLastTask.get();
      if (future == null) break;

      if (ApplicationManager.getApplication().isDispatchThread()) {
        UIUtil.dispatchAllInvocationEvents();
      }
      try {
        future.get(10, TimeUnit.MILLISECONDS);
        break;
      }
      catch (InterruptedException | ExecutionException e) {
        LOG.error(e);
      }
      catch (TimeoutException | CancellationException ignore) {
      }
    }
  }

  @TestOnly
  public void forceGoInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myUpdater.forceGo();
  }

  @Override
  @TestOnly
  public boolean ensureUpToDate(final boolean canBeCanceled) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      updateImmediately();
      return true;
    }
    VcsDirtyScopeVfsListener.getInstance(myProject).flushDirt();
    myUpdater.waitUntilRefreshed();
    waitUpdateAlarm();
    return true;
  }

  @Override
  public int getChangeListsNumber() {
    synchronized (myDataLock) {
      return myWorker.getChangeListsNumber();
    }
  }

  // only a light attempt to show that some dirty scope request is asynchronously coming
  // for users to see changes are not valid
  // (commit -> asynch synch VFS -> asynch vcs dirty scope)
  public void showLocalChangesInvalidated() {
    synchronized (myDataLock) {
      myShowLocalChangesInvalidated = true;
    }
  }

  public ChangelistConflictTracker getConflictTracker() {
    return myConflictTracker;
  }

  private static class MyChangesDeltaForwarder implements PlusMinusModify<BaseRevision> {
    private final RemoteRevisionsCache myRevisionsCache;
    private final ProjectLevelVcsManager myVcsManager;
    private final Project myProject;
    private final ChangeListManagerImpl.Scheduler myScheduler;

    public MyChangesDeltaForwarder(final Project project, @NotNull ChangeListManagerImpl.Scheduler scheduler) {
      myProject = project;
      myScheduler = scheduler;
      myRevisionsCache = RemoteRevisionsCache.getInstance(project);
      myVcsManager = ProjectLevelVcsManager.getInstance(project);
    }

    @Override
    public void modify(BaseRevision was, BaseRevision become) {
      doModify(was, become);
    }

    @Override
    public void plus(final BaseRevision baseRevision) {
      doModify(baseRevision, baseRevision);
    }

    @Override
    public void minus(final BaseRevision baseRevision) {
       myScheduler.submit(() -> {
         AbstractVcs vcs = getVcs(baseRevision);
         if (vcs != null) {
           myRevisionsCache.minus(Pair.create(baseRevision.getPath(), vcs));
         }
         BackgroundTaskUtil.syncPublisher(myProject, VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(baseRevision.getPath());
       });
     }

    private void doModify(BaseRevision was, BaseRevision become) {
      myScheduler.submit(() -> {
        final AbstractVcs vcs = getVcs(was);
        if (vcs != null) {
          myRevisionsCache.plus(Pair.create(was.getPath(), vcs));
        }
        BackgroundTaskUtil.syncPublisher(myProject, VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(become);
      });
    }

    @Nullable
    private AbstractVcs getVcs(@NotNull BaseRevision baseRevision) {
      VcsKey vcsKey = baseRevision.getVcs();
      if (vcsKey != null) {
        return myVcsManager.findVcsByName(vcsKey.getName());
      }
      return myVcsManager.getVcsFor(baseRevision.getFilePath());
    }
  }

  @Override
  public boolean isFreezedWithNotification(@Nullable String modalTitle) {
    final String freezeReason = isFreezed();
    if (freezeReason == null) return false;

    if (modalTitle != null) {
      Messages.showErrorDialog(myProject, freezeReason, modalTitle);
    }
    else {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, freezeReason, MessageType.WARNING);
    }
    return true;
  }

  static class Scheduler {
    private final AtomicReference<Future> myLastTask = new AtomicReference<>(); // @TestOnly
    private final ScheduledExecutorService myExecutor =
      AppExecutorUtil.createBoundedScheduledExecutorService("ChangeListManagerImpl pool", 1);

    public void schedule(@NotNull Runnable command, long delay, @NotNull TimeUnit unit) {
      myLastTask.set(myExecutor.schedule(command, delay, unit));
    }

    public void submit(@NotNull Runnable command) {
      myLastTask.set(myExecutor.submit(command));
    }
  }
}
