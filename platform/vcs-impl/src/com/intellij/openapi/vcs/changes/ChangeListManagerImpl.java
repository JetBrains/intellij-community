// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.CommonBundle;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value;
import com.intellij.openapi.vcs.changes.ChangeListWorker.ChangeListUpdater;
import com.intellij.openapi.vcs.changes.actions.ChangeListRemoveConfirmation;
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction;
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictTracker;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.ui.ChangeListDeltaListener;
import com.intellij.openapi.vcs.impl.*;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.commit.*;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import kotlin.text.StringsKt;
import kotlinx.coroutines.CoroutineScope;
import org.jdom.Element;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.awaitWithCheckCanceled;
import static com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static com.intellij.util.ui.UIUtil.BR;
import static java.util.stream.Collectors.toSet;

@State(name = "ChangeListManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ChangeListManagerImpl extends ChangeListManagerEx implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(ChangeListManagerImpl.class);

  @Topic.ProjectLevel
  public static final Topic<LocalChangeListsLoadedListener> LISTS_LOADED =
    new Topic<>(LocalChangeListsLoadedListener.class, Topic.BroadcastDirection.NONE);

  private final Project project;
  private final ChangelistConflictTracker myConflictTracker;

  private final ChangeListScheduler myScheduler; // update thread
  private final Disposable myUpdateDisposable = Disposer.newDisposable();

  private final EventDispatcher<ChangeListListener> myListeners = EventDispatcher.create(ChangeListListener.class);
  private final DelayedNotificator myDelayedNotificator; // notifies myListeners on the update thread

  private final Object myDataLock = new Object();

  private final UpdateRequestsQueue myUpdater;
  private final Modifier myModifier;

  private FileHolderComposite myComposite;
  private final ChangeListWorker myWorker;

  @Nullable private List<LocalChangeListImpl> myDisabledWorkerState;

  private boolean myInitialUpdate = true;
  private VcsException myUpdateException;
  private @NotNull List<Supplier<@Nullable JComponent>> myAdditionalInfo = Collections.emptyList();
  private volatile boolean myShowLocalChangesInvalidated;

  private volatile @Nls String myFreezeName;

  @NotNull private final Set<String> myListsToBeDeletedSilently = new HashSet<>();
  @NotNull private final Set<String> myListsToBeDeleted = new HashSet<>();
  private boolean myEmptyListDeletionScheduled;

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private boolean myModalNotificationsBlocked;

  private final List<CommitExecutor> myRegisteredCommitExecutors = new ArrayList<>();

  public static ChangeListManagerImpl getInstanceImpl(@NotNull Project project) {
    return (ChangeListManagerImpl)getInstance(project);
  }

  public ChangeListManagerImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    this.project = project;
    myConflictTracker = new ChangelistConflictTracker(project, this);

    myComposite = FileHolderComposite.create(project);
    myScheduler = new ChangeListScheduler(coroutineScope);
    myDelayedNotificator = new DelayedNotificator(this.project, this, myScheduler);
    myWorker = new ChangeListWorker(this.project, myDelayedNotificator);

    myUpdater = new UpdateRequestsQueue(this.project, myScheduler, this::updateImmediately, this::hasNothingToUpdate);
    myModifier = new Modifier(myWorker, myDelayedNotificator);

    MessageBusConnection busConnection = this.project.getMessageBus().connect(this);
    busConnection.subscribe(ChangeListListener.TOPIC, myListeners.getMulticaster());
    myListeners.addListener(new ChangeListAdapter() {
      @Override
      public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList, boolean automatic) {
        LocalChangeList oldList = (LocalChangeList)oldDefaultList;
        if (automatic || oldDefaultList == null || oldDefaultList.equals(newDefaultList)) {
          return;
        }

        scheduleAutomaticEmptyChangeListDeletion(oldList);
      }
    });

    VcsManagedFilesHolder.VCS_IGNORED_FILES_HOLDER_EP.addChangeListener(this.project, () -> {
      VcsDirtyScopeManager.getInstance(this.project).markEverythingDirty();
    }, this);
    VcsEP.EP_NAME.addChangeListener(() -> {
      resetChangedFiles();
      VcsDirtyScopeManager.getInstance(this.project).markEverythingDirty();
    }, this);

    CommitModeManager.subscribeOnCommitModeChange(busConnection, () -> updateChangeListAvailability());
    Registry.get("vcs.disable.changelists").addListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        updateChangeListAvailability();
      }
    }, this);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      busConnection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
        @Override
        public void projectClosing(@NotNull Project project) {
          if (project == ChangeListManagerImpl.this.project) {
            //noinspection TestOnlyProblems
            waitEverythingDoneInTestMode();
            Disposer.dispose(myUpdateDisposable);
          }
        }
      });
    }
    else {
      busConnection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
        @Override
        public void projectClosing(@NotNull Project project) {
          if (project == ChangeListManagerImpl.this.project) {
            // Can't use Project disposable - it will be called after pending tasks are finished
            Disposer.dispose(myUpdateDisposable);
          }
        }
      });
    }
  }

  @Override
  public void dispose() {
    myUpdater.stop();
  }

  @Override
  public void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list) {
    scheduleAutomaticEmptyChangeListDeletion(list, false);
  }

  @Override
  public void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList oldList, boolean silently) {
    if (!silently && oldList.hasDefaultName()) return;
    synchronized (myDataLock) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Schedule empty changelist deletion: %s, silently = %s", oldList.getName(), silently));
      }

      if (silently) {
        myListsToBeDeletedSilently.add(oldList.getId());
      }
      else {
        myListsToBeDeleted.add(oldList.getId());
      }

      if (!myEmptyListDeletionScheduled) {
        myEmptyListDeletionScheduled = true;
        invokeAfterUpdate(true, this::deleteEmptyChangeLists);
      }
    }
  }

  @RequiresEdt
  private void deleteEmptyChangeLists() {
    VcsConfiguration config = VcsConfiguration.getInstance(project);

    List<LocalChangeList> listsToBeDeletedSilently;
    List<LocalChangeList> listsToBeDeleted;

    Function<String, LocalChangeList> toDeleteMapping = id -> {
      LocalChangeList list = getChangeList(id);
      if (list == null || list.isDefault() || list.isReadOnly() || !list.getChanges().isEmpty()) return null;
      return list;
    };

    synchronized (myDataLock) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Empty changelist deletion, scheduled:\nsilently: %s\nasking: %s",
                                myListsToBeDeletedSilently, myListsToBeDeleted));
      }

      myListsToBeDeleted.removeAll(myListsToBeDeletedSilently);

      listsToBeDeletedSilently = mapNotNull(myListsToBeDeletedSilently, toDeleteMapping);
      myListsToBeDeletedSilently.clear();

      boolean askLater = myModalNotificationsBlocked &&
                         config.REMOVE_EMPTY_INACTIVE_CHANGELISTS == Value.SHOW_CONFIRMATION;
      if (!askLater) {
        listsToBeDeleted = mapNotNull(myListsToBeDeleted, toDeleteMapping);
        myListsToBeDeleted.clear();
      }
      else {
        listsToBeDeleted = Collections.emptyList();
      }

      myEmptyListDeletionScheduled = false;

      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Empty changelist deletion, to be deleted:\nsilently: %s\nasking: %s",
                                listsToBeDeletedSilently, listsToBeDeleted));
      }
    }

    if (config.REMOVE_EMPTY_INACTIVE_CHANGELISTS == Value.DO_NOTHING_SILENTLY ||
        config.REMOVE_EMPTY_INACTIVE_CHANGELISTS == Value.SHOW_CONFIRMATION &&
        ApplicationManager.getApplication().isUnitTestMode()) {
      listsToBeDeleted = Collections.emptyList();
    }

    ChangeListRemoveConfirmation.deleteEmptyInactiveLists(project, listsToBeDeletedSilently, toAsk -> true);

    ChangeListRemoveConfirmation.deleteEmptyInactiveLists(project, listsToBeDeleted,
                                                          toAsk -> config.REMOVE_EMPTY_INACTIVE_CHANGELISTS == Value.DO_ACTION_SILENTLY ||
                                                                   showRemoveEmptyChangeListsProposal(project, config, toAsk));
  }

  /**
   * Shows the proposal to delete one or more changelists that were default and became empty.
   *
   * @return true if the changelists have to be deleted, false if not.
   */
  private static boolean showRemoveEmptyChangeListsProposal(@NotNull Project project,
                                                            @NotNull final VcsConfiguration config,
                                                            @NotNull Collection<? extends ChangeList> lists) {
    if (lists.isEmpty()) {
      return false;
    }


    String changeListName = lists.size() == 1
                            ? StringUtil.first(lists.iterator().next().getName(), 30, true)
                            : StringUtil.join(lists, list -> StringUtil.first(list.getName(), 30, true), BR);
    String question = VcsBundle.message("changes.empty.changelists.no.longer.active", lists.size(), changeListName);


    VcsConfirmationDialog dialog =
      new VcsConfirmationDialog(project, VcsBundle.message("dialog.title.remove.empty.changelist"), VcsBundle.message("button.remove"),
                                CommonBundle.getCancelButtonText(), new VcsShowConfirmationOption() {
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
      }, XmlStringUtil.wrapInHtml(question), VcsBundle.message("checkbox.remember.my.choice"));
    return dialog.showAndGet();
  }

  @Override
  @RequiresEdt
  public void blockModalNotifications() {
    myModalNotificationsBlocked = true;
  }

  @Override
  @RequiresEdt
  public void unblockModalNotifications() {
    myModalNotificationsBlocked = false;
    deleteEmptyChangeLists();
  }

  private void startUpdater() {
    myUpdater.initialized();
    project.getMessageBus().syncPublisher(LISTS_LOADED).processLoadedLists(getChangeLists());

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(VCS_CONFIGURATION_CHANGED, () -> VcsDirtyScopeManager.getInstance(project).markEverythingDirty());
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myConflictTracker.startTracking();
    }
  }

  static final class MyStartupActivity implements VcsStartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      getInstanceImpl(project).startUpdater();
    }

    @Override
    public int getOrder() {
      return VcsInitObject.CHANGE_LIST_MANAGER.getOrder();
    }
  }

  @ApiStatus.Internal
  public void registerChangeTracker(@NotNull FilePath filePath, @NotNull ChangeListWorker.PartialChangeTracker tracker) {
    synchronized (myDataLock) {
      myWorker.registerChangeTracker(filePath, tracker);
    }
  }

  @ApiStatus.Internal
  public void unregisterChangeTracker(@NotNull FilePath filePath, @NotNull ChangeListWorker.PartialChangeTracker tracker) {
    synchronized (myDataLock) {
      myWorker.unregisterChangeTracker(filePath, tracker);
    }
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
    myUpdater.invokeAfterUpdate(afterUpdate, mode, title);
  }

  @Override
  public void freeze(@NotNull String reason) {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
    }

    myUpdater.setIgnoreBackgroundOperation(true);
    Semaphore sem = new Semaphore(1);

    invokeAfterUpdate(false, () -> {
      myUpdater.setIgnoreBackgroundOperation(false);
      myUpdater.pause();
      myFreezeName = reason;
      sem.up();
    });

    awaitWithCheckCanceled(sem, ProgressManager.getInstance().getProgressIndicator());
  }

  @Override
  public void unfreeze() {
    myUpdater.go();
    myFreezeName = null;
  }

  @Override
  public void waitForUpdate() {
    assert !ApplicationManager.getApplication().isReadAccessAllowed();
    CountDownLatch waiter = new CountDownLatch(1);
    invokeAfterUpdate(false, waiter::countDown);
    awaitWithCheckCanceled(waiter);
  }

  @Override
  public @NotNull Promise<?> promiseWaitForUpdate() {
    AsyncPromise<Boolean> promise = new AsyncPromise<>();
    invokeAfterUpdate(false, () -> promise.setResult(true));
    return promise;
  }

  @Override
  public String isFreezed() {
    return myFreezeName;
  }

  public void executeOnUpdaterThread(@NotNull Runnable r) {
    myScheduler.submit(r);
  }

  public void executeUnderDataLock(@NotNull Runnable r) {
    ApplicationManager.getApplication().runReadAction(() -> {
      synchronized (myDataLock) {
        r.run();
      }
    });
  }

  @Override
  public void scheduleUpdate() {
    scheduleUpdateImpl();
  }

  @ApiStatus.Internal
  public void scheduleUpdateImpl() {
    myUpdater.schedule();
  }

  private void resetChangedFiles() {
    try {
      synchronized (myDataLock) {
        DataHolder dataHolder = new DataHolder(myComposite.copy(), new ChangeListUpdater(myWorker), true);
        dataHolder.notifyStart();
        dataHolder.notifyEnd();

        dataHolder.finish();
        myWorker.applyChangesFromUpdate(dataHolder.getUpdatedWorker(), new MyChangesDeltaForwarder(project, myScheduler));
        myComposite = dataHolder.getComposite();

        myUpdateException = null;
        myAdditionalInfo = Collections.emptyList();
      }

      myDelayedNotificator.changedFileStatusChanged(true);
      myDelayedNotificator.unchangedFileStatusChanged(true);
      myDelayedNotificator.changeListUpdateDone();
      ChangesViewManager.getInstanceEx(project).resetViewImmediatelyAndRefreshLater();
    }
    catch (Exception | AssertionError ex) {
      LOG.error(ex);
    }
  }

  /**
   * @return true if {@link #updateImmediately()} can be skipped.
   */
  private boolean hasNothingToUpdate() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (!vcsManager.hasActiveVcss()) return true;

    VcsDirtyScopeManagerImpl dirtyScopeManager = VcsDirtyScopeManagerImpl.getInstanceImpl(project);
    return !dirtyScopeManager.hasDirtyScopes();
  }

  /**
   * @return false if update was re-scheduled due to new 'markEverythingDirty' event, true otherwise.
   */
  private boolean updateImmediately() {
    return BackgroundTaskUtil.runUnderDisposeAwareIndicator(myUpdateDisposable, () -> {
      final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
      if (!vcsManager.hasActiveVcss()) return true;

      VcsDirtyScopeManagerImpl dirtyScopeManager = VcsDirtyScopeManagerImpl.getInstanceImpl(project);
      final VcsInvalidated invalidated = dirtyScopeManager.retrieveScopes();
      if (checkScopeIsEmpty(invalidated)) {
        LOG.debug("[update] - dirty scope is empty");
        dirtyScopeManager.changesProcessed();
        return true;
      }

      final boolean wasEverythingDirty = invalidated.isEverythingDirty();
      final List<VcsModifiableDirtyScope> scopes = invalidated.getScopes();

      boolean isInitialUpdate;
      ChangesViewEx changesView = ChangesViewManager.getInstanceEx(project);
      try {
        if (myUpdater.isStopped()) return true;

        // copy existing data to objects that would be updated.
        // mark for "modifier" that update started (it would create duplicates of modification commands done by user during update;
        // after update of copies of objects is complete, it would apply the same modifications to copies.)
        final DataHolder dataHolder;
        synchronized (myDataLock) {
          dataHolder = new DataHolder(myComposite.copy(), new ChangeListUpdater(myWorker), wasEverythingDirty);
          myModifier.enterUpdate();
          if (wasEverythingDirty) {
            myUpdateException = null;
            myAdditionalInfo = Collections.emptyList();
          }

          if (LOG.isDebugEnabled()) {
            String scopeInString = StringUtil.join(scopes, Object::toString, "->\n");
            LOG.debug("refresh procedure started, everything: " + wasEverythingDirty + " dirty scope: " + scopeInString +
                      "\nignored: " + myComposite.getIgnoredFileHolder().getFiles().size() +
                      "\nunversioned: " + myComposite.getUnversionedFileHolder().getFiles().size() +
                      "\ncurrent changes: " + myWorker);
          }

          isInitialUpdate = myInitialUpdate;
          myInitialUpdate = false;
        }
        changesView.setBusy(true);
        changesView.scheduleRefresh();

        SensitiveProgressWrapper vcsIndicator = new SensitiveProgressWrapper(ProgressManager.getInstance().getProgressIndicator());
        if (!isInitialUpdate) invalidated.doWhenCanceled(() -> vcsIndicator.cancel());

        try {
          ProgressManager.getInstance().executeProcessUnderProgress(() -> {
            iterateScopes(dataHolder, scopes, vcsIndicator);
          }, vcsIndicator);
        }
        catch (ProcessCanceledException ignore) {
        }
        boolean wasCancelled = vcsIndicator.isCanceled();

        // for the case of project being closed we need a read action here -> to be more consistent
        ApplicationManager.getApplication().runReadAction(() -> {
          if (project.isDisposed()) return;

          synchronized (myDataLock) {
            ChangeListWorker updatedWorker = dataHolder.getUpdatedWorker();
            boolean takeChanges = myUpdateException == null && !wasCancelled &&
                                  updatedWorker.areChangeListsEnabled() == myWorker.areChangeListsEnabled();

            // update member from copy
            if (takeChanges) {
              dataHolder.finish();
              // do same modifications to change lists as was done during update + do delayed notifications
              myModifier.finishUpdate(updatedWorker);

              myWorker.applyChangesFromUpdate(updatedWorker, new MyChangesDeltaForwarder(project, myScheduler));

              if (LOG.isDebugEnabled()) {
                LOG.debug("refresh procedure finished, unversioned size: " +
                          dataHolder.getComposite().getUnversionedFileHolder().getFiles().size() +
                          "\nchanges: " + myWorker);
              }
              final boolean statusChanged = !myComposite.equals(dataHolder.getComposite());
              myComposite = dataHolder.getComposite();
              if (statusChanged) {
                boolean isUnchangedUpdating = isInUpdate() || isUnversionedInUpdateMode() || isIgnoredInUpdateMode();
                myDelayedNotificator.unchangedFileStatusChanged(!isUnchangedUpdating);
              }
              LOG.debug("[update] - success");
            }
            else {
              myModifier.finishUpdate(null);
              LOG.debug(String.format("[update] - aborted, wasCancelled: %s", wasCancelled));
            }
            myShowLocalChangesInvalidated = false;
          }
        });

        for (VcsDirtyScope scope : scopes) {
          if (scope.getVcs().isTrackingUnchangedContent()) {
            VcsRootIterator.iterateExistingInsideScope(scope, file -> {
              LastUnchangedContentTracker.markUntouched(file); //todo what if it has become dirty again during update?
              return true;
            });
          }
        }

        return !wasCancelled;
      }
      catch (ProcessCanceledException e) {
        // OK, we're finishing all the stuff now.
      }
      catch (Exception | AssertionError ex) {
        LOG.error(ex);
      }
      finally {
        dirtyScopeManager.changesProcessed();

        myDelayedNotificator.changedFileStatusChanged(!isInUpdate());
        myDelayedNotificator.changeListUpdateDone();
        changesView.scheduleRefresh();
      }
      return true;
    });
  }

  private static boolean checkScopeIsEmpty(VcsInvalidated invalidated) {
    if (invalidated == null) return true;
    if (invalidated.isEverythingDirty()) return false;
    return invalidated.isEmpty();
  }

  private void iterateScopes(@NotNull DataHolder dataHolder,
                             @NotNull List<? extends VcsModifiableDirtyScope> scopes,
                             @NotNull ProgressIndicator indicator) {
    ChangeListUpdater updater = dataHolder.getChangeListUpdater();
    FileHolderComposite composite = dataHolder.getComposite();
    Supplier<Boolean> disposedGetter = () -> project.isDisposed() || myUpdater.isStopped();

    List<Supplier<@Nullable JComponent>> additionalInfos = new ArrayList<>();

    dataHolder.notifyStart();
    try {
      for (VcsModifiableDirtyScope scope : scopes) {
        indicator.checkCanceled();

        // do actual requests about file statuses
        UpdatingChangeListBuilder builder = new UpdatingChangeListBuilder(scope, updater, composite, disposedGetter);
        actualUpdate(builder, scope, dataHolder, updater, indicator);
        additionalInfos.addAll(builder.getAdditionalInfo());

        synchronized (myDataLock) {
          if (myUpdateException != null) break;
        }
      }
    }
    finally {
      dataHolder.notifyEnd();
    }

    synchronized (myDataLock) {
      myAdditionalInfo = additionalInfos;
    }
  }

  private final class DataHolder {
    private final boolean myWasEverythingDirty;
    private final FileHolderComposite myComposite;
    private final ChangeListUpdater myChangeListUpdater;

    private DataHolder(FileHolderComposite composite, ChangeListUpdater changeListUpdater, boolean wasEverythingDirty) {
      myComposite = composite;
      myChangeListUpdater = changeListUpdater;
      myWasEverythingDirty = wasEverythingDirty;
    }

    private void notifyStart() {
      if (myWasEverythingDirty) {
        myComposite.cleanAll();
        myChangeListUpdater.notifyStartProcessingChanges(null);
      }
    }

    private void notifyStartProcessingChanges(@NotNull VcsModifiableDirtyScope scope) {
      if (!myWasEverythingDirty) {
        myComposite.cleanUnderScope(scope);
        myChangeListUpdater.notifyStartProcessingChanges(scope);
      }

      myComposite.notifyVcsStarted(scope.getVcs());
    }

    private void notifyDoneProcessingChanges(@NotNull VcsDirtyScope scope) {
      if (!myWasEverythingDirty) {
        myChangeListUpdater.notifyDoneProcessingChanges(myDelayedNotificator, scope);
      }
    }

    void notifyEnd() {
      if (myWasEverythingDirty) {
        myChangeListUpdater.notifyDoneProcessingChanges(myDelayedNotificator, null);
      }
    }

    public void finish() {
      myChangeListUpdater.finish();
    }

    @NotNull
    public FileHolderComposite getComposite() {
      return myComposite;
    }

    @NotNull
    public ChangeListUpdater getChangeListUpdater() {
      return myChangeListUpdater;
    }

    @NotNull
    public ChangeListWorker getUpdatedWorker() {
      return myChangeListUpdater.getUpdatedWorker();
    }
  }

  private void actualUpdate(@NotNull UpdatingChangeListBuilder builder,
                            @NotNull VcsModifiableDirtyScope scope,
                            @NotNull DataHolder dataHolder,
                            @NotNull ChangeListManagerGate gate,
                            @NotNull ProgressIndicator indicator) {
    dataHolder.notifyStartProcessingChanges(scope);
    try {
      AbstractVcs vcs = scope.getVcs();
      ChangeProvider changeProvider = vcs.getChangeProvider();
      if (changeProvider != null) {
        StructuredIdeActivity activity = VcsStatisticsCollector.logClmRefresh(project, vcs, scope.wasEveryThingDirty());
        changeProvider.getChanges(scope, builder, indicator, gate);
        activity.finished();
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
      ExceptionUtil.rethrow(t);
    }
    finally {
      if (!myUpdater.isStopped()) {
        dataHolder.notifyDoneProcessingChanges(scope);
      }
    }
  }

  private void handleUpdateException(final VcsException e) {
    LOG.info(e);

    if (e instanceof VcsConnectionProblem) {
      ApplicationManager.getApplication().invokeLater(() -> ((VcsConnectionProblem)e).attemptQuickFix(false));
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
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

  public static boolean isUnder(@NotNull Change change, @NotNull VcsDirtyScope scope) {
    final ContentRevision before = change.getBeforeRevision();
    final ContentRevision after = change.getAfterRevision();
    return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
  }

  @Override
  @NotNull
  public List<LocalChangeList> getChangeLists() {
    synchronized (myDataLock) {
      return myWorker.getChangeLists();
    }
  }

  @NotNull
  @Override
  public List<File> getAffectedPaths() {
    List<FilePath> filePaths;
    synchronized (myDataLock) {
      filePaths = myWorker.getAffectedPaths();
    }
    return mapNotNull(filePaths, FilePath::getIOFile);
  }

  @Override
  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    List<FilePath> filePaths;
    synchronized (myDataLock) {
      filePaths = myWorker.getAffectedPaths();
    }
    return mapNotNull(filePaths, FilePath::getVirtualFile);
  }

  @Override
  @NotNull
  public Collection<Change> getAllChanges() {
    synchronized (myDataLock) {
      return myWorker.getAllChanges();
    }
  }

  public boolean isUnversionedInUpdateMode() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getUnversionedFileHolder().isInUpdatingMode();
      }
    });
  }

  /**
   * @deprecated use {@link #getUnversionedFilesPaths}
   */
  @Deprecated
  @NotNull
  public List<VirtualFile> getUnversionedFiles() {
    return mapNotNull(getUnversionedFilesPaths(), FilePath::getVirtualFile);
  }

  @NotNull
  @Override
  public List<FilePath> getUnversionedFilesPaths() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return new ArrayList<>(myComposite.getUnversionedFileHolder().getFiles());
      }
    });
  }

  @NotNull
  @Override
  public List<VirtualFile> getModifiedWithoutEditing() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getModifiedWithoutEditingFileHolder().getFiles();
      }
    });
  }

  @NotNull
  @Override
  public List<FilePath> getIgnoredFilePaths() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return new ArrayList<>(myComposite.getIgnoredFileHolder().getFiles());
      }
    });
  }

  public boolean isIgnoredInUpdateMode() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getIgnoredFileHolder().isInUpdatingMode();
      }
    });
  }

  public List<VirtualFile> getLockedFolders() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getLockedFileHolder().getFiles();
      }
    });
  }

  public Map<VirtualFile, LogicalLock> getLogicallyLockedFolders() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return new HashMap<>(myComposite.getLogicallyLockedFileHolder().getMap());
      }
    });
  }

  public boolean isLogicallyLocked(final VirtualFile file) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getLogicallyLockedFileHolder().containsKey(file);
      }
    });
  }

  public boolean isContainedInLocallyDeleted(final FilePath filePath) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getDeletedFileHolder().isContainedInLocallyDeleted(filePath);
      }
    });
  }

  public List<LocallyDeletedChange> getDeletedFiles() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getDeletedFileHolder().getFiles();
      }
    });
  }

  public MultiMap<String, VirtualFile> getSwitchedFilesMap() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getSwitchedFileHolder().getBranchToFileMap();
      }
    });
  }

  @Nullable
  public Map<VirtualFile, String> getSwitchedRoots() {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getRootSwitchFileHolder().getFilesMapCopy();
      }
    });
  }

  public VcsException getUpdateException() {
    synchronized (myDataLock) {
      return myUpdateException;
    }
  }

  public @NotNull List<Supplier<@Nullable JComponent>> getAdditionalUpdateInfo() {
    synchronized (myDataLock) {
      List<Supplier<JComponent>> updateInfo = new ArrayList<>();
      if (myUpdateException != null) {
        String errorMessage = VcsBundle.message("error.updating.changes", myUpdateException.getMessage());
        updateInfo.add(ChangesViewManager.createTextStatusFactory(errorMessage, true));
      }
      updateInfo.addAll(myAdditionalInfo);
      return updateInfo;
    }
  }

  @Override
  public boolean isFileAffected(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) return false;
    synchronized (myDataLock) {
      return myWorker.getStatus(file) != null;
    }
  }

  @Override
  @Nullable
  public LocalChangeList findChangeList(final String name) {
    synchronized (myDataLock) {
      return myWorker.getChangeListByName(name);
    }
  }

  @Override
  @Nullable
  public LocalChangeList getChangeList(@Nullable String id) {
    synchronized (myDataLock) {
      return myWorker.getChangeListById(id);
    }
  }

  private void scheduleChangesViewRefresh() {
    if (!project.isDisposed()) {
      ChangesViewManager.getInstance(project).scheduleRefresh();
    }
  }

  @Override
  public @NotNull LocalChangeList addChangeList(@NotNull String name, @Nullable String comment) {
    return addChangeList(name, comment, null);
  }

  @Override
  public @NotNull LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable ChangeListData data) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final LocalChangeList changeList = myModifier.addChangeList(name, comment, data);
        scheduleChangesViewRefresh();
        return changeList;
      }
    });
  }


  @Override
  public void removeChangeList(@NotNull String name) {
    ApplicationManager.getApplication().runReadAction(() -> {
      synchronized (myDataLock) {
        myModifier.removeChangeList(name);
        scheduleChangesViewRefresh();
      }
    });
  }

  @Override
  public void removeChangeList(@NotNull LocalChangeList list) {
    removeChangeList(list.getName());
  }

  public void setDefaultChangeList(@NotNull String name, boolean automatic) {
    ApplicationManager.getApplication().runReadAction(() -> {
      synchronized (myDataLock) {
        myModifier.setDefault(name, automatic);
        scheduleChangesViewRefresh();
      }
    });
  }

  @Override
  public void setDefaultChangeList(@NotNull String name) {
    setDefaultChangeList(name, false);
  }

  @Override
  public void setDefaultChangeList(@NotNull final LocalChangeList list) {
    setDefaultChangeList(list, false);
  }

  @Override
  public void setDefaultChangeList(@NotNull final LocalChangeList list, boolean automatic) {
    setDefaultChangeList(list.getName(), automatic);
  }

  @Override
  public boolean setReadOnly(@NotNull String name, final boolean value) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final boolean result = myModifier.setReadOnly(name, value);
        scheduleChangesViewRefresh();
        return result;
      }
    });
  }

  @Override
  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final boolean result = myModifier.editName(fromName, toName);
        scheduleChangesViewRefresh();
        return result;
      }
    });
  }

  @Override
  public String editComment(@NotNull String name, String newComment) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final String oldComment = myModifier.editComment(name, StringUtil.notNullize(newComment));
        scheduleChangesViewRefresh();
        return oldComment;
      }
    });
  }

  @Override
  public boolean editChangeListData(@NotNull String name, @Nullable ChangeListData newData) {
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        final boolean result = myModifier.editData(name, newData);
        scheduleChangesViewRefresh();
        return result;
      }
    });
  }

  @Override
  public void moveChangesTo(@NotNull LocalChangeList list, Change @NotNull ... changes) {
    moveChangesTo(list, ContainerUtil.skipNulls(Arrays.asList(changes)));
  }

  @Override
  public void moveChangesTo(@NotNull LocalChangeList list, @NotNull List<? extends @NotNull Change> changes) {
    ApplicationManager.getApplication().runReadAction(() -> {
      synchronized (myDataLock) {
        myModifier.moveChangesTo(list.getName(), changes);
        scheduleChangesViewRefresh();
      }
    });
  }

  @NotNull
  @Override
  public LocalChangeList getDefaultChangeList() {
    synchronized (myDataLock) {
      return myWorker.getDefaultList();
    }
  }

  @NotNull
  @Override
  public String getDefaultListName() {
    synchronized (myDataLock) {
      return myWorker.getDefaultList().getName();
    }
  }

  @ApiStatus.Internal
  public void notifyChangelistsChanged(@NotNull FilePath path,
                                       @NotNull List<String> beforeChangeListsIds,
                                       @NotNull List<String> afterChangeListsIds) {
    myWorker.notifyChangelistsChanged(path, beforeChangeListsIds, afterChangeListsIds);
  }

  /**
   * Notify that {@link VcsManagedFilesHolder} state was changed.
   */
  public void notifyUnchangedFileStatusChanged() {
    boolean isUnchangedUpdating = isInUpdate() || isUnversionedInUpdateMode() || isIgnoredInUpdateMode();
    myDelayedNotificator.unchangedFileStatusChanged(!isUnchangedUpdating);
    myDelayedNotificator.changeListUpdateDone();
  }

  @Override
  public String getChangeListNameIfOnlyOne(final Change[] changes) {
    synchronized (myDataLock) {
      List<LocalChangeList> lists = myWorker.getAffectedLists(Arrays.asList(changes));
      return lists.size() == 1 ? lists.get(0).getName() : null;
    }
  }

  @Override
  public boolean isInUpdate() {
    return myModifier.isInsideUpdate() || myShowLocalChangesInvalidated;
  }

  @Override
  @Nullable
  public Change getChange(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) return null;
    return getChange(VcsUtil.getFilePath(file));
  }

  @Override
  @NotNull
  public List<LocalChangeList> getAffectedLists(@NotNull Collection<? extends Change> changes) {
    synchronized (myDataLock) {
      return myWorker.getAffectedLists(changes);
    }
  }

  @NotNull
  @Override
  public List<LocalChangeList> getChangeLists(@NotNull Change change) {
    return getAffectedLists(Collections.singletonList(change));
  }

  @NotNull
  @Override
  public List<LocalChangeList> getChangeLists(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) return Collections.emptyList();
    synchronized (myDataLock) {
      Change change = myWorker.getChangeForPath(VcsUtil.getFilePath(file));
      if (change == null) return Collections.emptyList();
      return getChangeLists(change);
    }
  }

  @Override
  @Nullable
  public LocalChangeList getChangeList(@NotNull Change change) {
    return ContainerUtil.getFirstItem(getChangeLists(change));
  }

  @Override
  @Nullable
  public LocalChangeList getChangeList(@NotNull VirtualFile file) {
    return ContainerUtil.getFirstItem(getChangeLists(file));
  }

  @Override
  @Nullable
  public Change getChange(final FilePath file) {
    synchronized (myDataLock) {
      return myWorker.getChangeForPath(file);
    }
  }

  @Override
  public boolean isUnversioned(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) return false;
    VcsRoot vcsRoot;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-322445, EA-857508")) {
      vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(file);
      if (vcsRoot == null) return false;
    }

    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getUnversionedFileHolder().containsFile(VcsUtil.getFilePath(file), vcsRoot);
      }
    });
  }

  @NotNull
  @Override
  public FileStatus getStatus(@NotNull FilePath path) {
    return getStatus(path, path.getVirtualFile());
  }

  @Override
  @NotNull
  public FileStatus getStatus(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) return FileStatus.NOT_CHANGED;
    return getStatus(VcsUtil.getFilePath(file), file);
  }

  @NotNull
  private FileStatus getStatus(@NotNull FilePath path, @Nullable VirtualFile file) {
    VcsRoot vcsRoot = file != null ? ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(file)
                                   : ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(path);
    if (vcsRoot == null) return FileStatus.NOT_CHANGED;

    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        if (myComposite.getUnversionedFileHolder().containsFile(path, vcsRoot)) return FileStatus.UNKNOWN;
        if (file != null && myComposite.getModifiedWithoutEditingFileHolder().containsFile(file)) return FileStatus.HIJACKED;
        if (myComposite.getIgnoredFileHolder().containsFile(path, vcsRoot)) return FileStatus.IGNORED;

        FileStatus status = ObjectUtils.notNull(myWorker.getStatus(path), FileStatus.NOT_CHANGED);

        if (file != null && FileStatus.NOT_CHANGED.equals(status)) {
          boolean switched = myComposite.getSwitchedFileHolder().containsFile(file);
          if (switched) return FileStatus.SWITCHED;
        }

        return status;
      }
    });
  }

  @Override
  @NotNull
  public Collection<Change> getChangesIn(@NotNull VirtualFile dir) {
    if (!dir.isInLocalFileSystem()) return Collections.emptySet();
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
    return getAllChanges().stream().filter(change -> isChangeUnder(dirPath, change)).collect(toSet());
  }

  private static boolean isChangeUnder(@NotNull FilePath parent, @NotNull Change change) {
    FilePath after = ChangesUtil.getAfterPath(change);
    FilePath before = ChangesUtil.getBeforePath(change);
    return after != null && after.isUnder(parent, false) ||
           !Comparing.equal(before, after) && before != null && before.isUnder(parent, false);
  }

  @Override
  public void addUnversionedFiles(@Nullable final LocalChangeList list, @NotNull final List<? extends VirtualFile> files) {
    ScheduleForAdditionAction.Manager.addUnversionedFilesToVcs(project, list, files);
  }

  @Override
  public void addChangeListListener(@NotNull ChangeListListener listener, @NotNull Disposable disposable) {
    myListeners.addListener(listener, disposable);
  }

  @Override
  public void addChangeListListener(@NotNull ChangeListListener listener) {
    myListeners.addListener(listener);
  }

  @Override
  public void removeChangeListListener(@NotNull ChangeListListener listener) {
    myListeners.removeListener(listener);
  }

  @SuppressWarnings("removal")
  @Override
  public void registerCommitExecutor(@NotNull CommitExecutor executor) {
    myRegisteredCommitExecutors.add(executor);
  }

  @Override
  public void commitChanges(@NotNull LocalChangeList changeList, @NotNull List<? extends Change> changes) {
    doCommit(changeList, changes, false);
  }

  private void doCommit(final LocalChangeList changeList, final List<? extends Change> changes, final boolean synchronously) {
    FileDocumentManager.getInstance().saveAllDocuments();

    String commitMessage = StringUtil.isEmpty(changeList.getComment()) ? changeList.getName() : changeList.getComment();
    ChangeListCommitState commitState = new ChangeListCommitState(changeList, changes, commitMessage);
    LocalChangesCommitter committer = SingleChangeListCommitter.create(project, commitState, new CommitContext(), changeList.getName());

    committer.addResultHandler(new ShowNotificationCommitResultHandler(committer));
    committer.runCommit(changeList.getName(), synchronously);
  }

  @TestOnly
  public void commitChangesSynchronouslyWithResult(@NotNull LocalChangeList changeList, @NotNull List<? extends Change> changes) {
    doCommit(changeList, changes, true);
  }

  @Override
  public void loadState(@NotNull Element element) {
    List<LocalChangeListImpl> changeLists = ChangeListManagerSerialization.readExternal(element, project);

    synchronized (myDataLock) {
      if (!myInitialUpdate) {
        LOG.warn("Local changes overwritten");
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
      }

      boolean areChangeListsEnabled = shouldEnableChangeLists();
      myWorker.setChangeListsEnabled(areChangeListsEnabled);

      if (areChangeListsEnabled) {
        myWorker.setChangeLists(changeLists);
      }
      else {
        myDisabledWorkerState = changeLists;
      }
    }
    myConflictTracker.loadState(element);
  }

  @Override
  public @NotNull Element getState() {
    Element element = new Element("state");

    boolean areChangeListsEnabled;
    List<? extends LocalChangeList> changesToSave;
    synchronized (myDataLock) {
      areChangeListsEnabled = myWorker.areChangeListsEnabled();
      changesToSave = areChangeListsEnabled ? myWorker.getChangeLists() : myDisabledWorkerState;
    }
    ChangeListManagerSerialization.writeExternal(element, changesToSave, areChangeListsEnabled);
    myConflictTracker.saveState(element);
    return element;
  }

  // used in TeamCity
  @SuppressWarnings("removal")
  @Override
  public void reopenFiles(@NotNull List<? extends FilePath> paths) {
    final ReadonlyStatusHandlerImpl readonlyStatusHandler = (ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(project);
    final boolean savedOption = readonlyStatusHandler.getState().SHOW_DIALOG;
    readonlyStatusHandler.getState().SHOW_DIALOG = false;
    try {
      readonlyStatusHandler.ensureFilesWritable(mapNotNull(paths, FilePath::getVirtualFile));
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

  @SuppressWarnings("removal")
  @Override
  public void addDirectoryToIgnoreImplicitly(@NotNull String path) {
  }

  @SuppressWarnings("removal")
  @Override
  public void setFilesToIgnore(IgnoredFileBean @NotNull ... filesToIgnore) {
  }

  @SuppressWarnings("removal")
  @Override
  public IgnoredFileBean @NotNull [] getFilesToIgnore() {
    return EMPTY_ARRAY;
  }

  private static final IgnoredFileBean[] EMPTY_ARRAY = new IgnoredFileBean[0];

  @Override
  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) return false;
    return isIgnoredFile(VcsUtil.getFilePath(file));
  }

  @Override
  public boolean isIgnoredFile(@NotNull FilePath file) {
    VcsRoot vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(file);
    if (vcsRoot == null) return false;

    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getIgnoredFileHolder().containsFile(file, vcsRoot);
      }
    });
  }

  public static final class DefaultIgnoredFileProvider implements IgnoredFileProvider {
    @Override
    public boolean isIgnoredFile(@NotNull Project project, @NotNull FilePath filePath) {
      IProjectStore store = ProjectKt.getStateStore(project);
      if (!ProjectKt.isDirectoryBased(project) && FileUtilRt.extensionEquals(filePath.getPath(), WorkspaceFileType.DEFAULT_EXTENSION)) {
        return true; // *.iws
      }

      if (StringsKt.equals(filePath.getPath(),
                           FileUtil.toSystemIndependentName(store.getWorkspacePath().toString()),
                           !SystemInfo.isFileSystemCaseSensitive)) {
        return true; // workspace.xml
      }

      if (isShelfDirOrInsideIt(filePath, project)) {
        return true; // .idea/shelf
      }

      return false;
    }

    private static boolean isShelfDirOrInsideIt(@NotNull FilePath filePath, @NotNull Project project) {
      String shelfPath = ShelveChangesManager.getShelfPath(project);
      return FileUtil.isAncestor(shelfPath, filePath.getPath(), false);
    }

    @NotNull
    @Override
    public Set<IgnoredFileDescriptor> getIgnoredFiles(@NotNull Project project) {
      Set<IgnoredFileBean> ignored = new LinkedHashSet<>();

      String shelfPath = ShelveChangesManager.getShelfPath(project);
      ignored.add(IgnoredBeanFactory.ignoreUnderDirectory(shelfPath, project));

      Path workspaceFile = ProjectKt.getStateStore(project).getWorkspacePath();
      ignored.add(IgnoredBeanFactory.ignoreFile(workspaceFile.toString().replace(File.separatorChar, '/'), project));
      return ContainerUtil.unmodifiableOrEmptySet(ignored);
    }

    @NotNull
    @Override
    public String getIgnoredGroupDescription() {
      return VcsBundle.message("changes.text.default.ignored.files");
    }
  }

  @Override
  @Nullable
  public String getSwitchedBranch(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) return null;
    return ReadAction.compute(() -> {
      synchronized (myDataLock) {
        return myComposite.getSwitchedFileHolder().getBranchForFile(file);
      }
    });
  }

  @TestOnly
  public void waitUntilRefreshed() {
    LOG.debug("waitUntilRefreshed");
    assert ApplicationManager.getApplication().isUnitTestMode();
    project.getService(VcsDirtyScopeVfsListener.class).waitForAsyncTaskCompletion();
    myUpdater.waitUntilRefreshed();
    waitUpdateAlarm();
  }

  @TestOnly
  private void waitUpdateAlarm() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    myScheduler.submit(semaphore::up);
    if (ApplicationManager.getApplication().isDispatchThread()) {
      while (!semaphore.waitFor(100)) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    else {
      semaphore.waitFor();
    }
    LOG.debug("waitUpdateAlarm - finished");
  }

  @TestOnly
  public void stopEveryThingIfInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myScheduler.cancelAll();
  }

  @TestOnly
  public void waitEverythingDoneAndStopInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myScheduler.awaitAllAndStop();
    myUpdater.stop();
  }

  @TestOnly
  public void waitEverythingDoneInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myScheduler.awaitAll();
    LOG.debug("waitEverythingDoneInTestMode - finished");
  }

  @TestOnly
  public void forceStopInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myUpdater.stop();
  }

  @TestOnly
  public void forceGoInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myUpdater.forceGo();
  }

  @TestOnly
  public void ensureUpToDate() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    waitUntilRefreshed();
  }

  @RequiresEdt
  private void updateChangeListAvailability() {
    if (project.isDisposed()) return;

    boolean enabled = shouldEnableChangeLists();
    synchronized (myDataLock) {
      if (enabled == myWorker.areChangeListsEnabled()) return;
    }

    project.getMessageBus().syncPublisher(ChangeListAvailabilityListener.TOPIC).onBefore();

    synchronized (myDataLock) {
      assert enabled != myWorker.areChangeListsEnabled();

      if (!enabled) {
        myDisabledWorkerState = myWorker.getChangeListsImpl();
      }

      myWorker.setChangeListsEnabled(enabled);

      if (enabled) {
        if (myDisabledWorkerState != null) {
          myWorker.setChangeLists(myDisabledWorkerState);
        }

        // Schedule refresh to replace FakeRevisions with actual changes
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
        ChangesViewManager.getInstance(project).scheduleRefresh();
      }
    }

    project.getMessageBus().syncPublisher(ChangeListAvailabilityListener.TOPIC).onAfter();
  }

  private boolean shouldEnableChangeLists() {
    boolean forceDisable = CommitModeManager.getInstance(project).getCurrentCommitMode().hideLocalChangesTab() ||
                           Registry.is("vcs.disable.changelists", false);
    return !forceDisable;
  }

  @Override
  public boolean areChangeListsEnabled() {
    synchronized (myDataLock) {
      return myWorker.areChangeListsEnabled();
    }
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
    myShowLocalChangesInvalidated = true;
  }

  @ApiStatus.Internal
  public ChangelistConflictTracker getConflictTracker() {
    return myConflictTracker;
  }

  private static final class MyChangesDeltaForwarder implements ChangeListDeltaListener {
    private final RemoteRevisionsCache myRevisionsCache;
    private final ProjectLevelVcsManager myVcsManager;
    private final Project myProject;
    private final ChangeListScheduler myScheduler;

    MyChangesDeltaForwarder(final Project project, @NotNull ChangeListScheduler scheduler) {
      myProject = project;
      myScheduler = scheduler;
      myRevisionsCache = RemoteRevisionsCache.getInstance(project);
      myVcsManager = ProjectLevelVcsManager.getInstance(project);
    }

    @Override
    public void modified(@NotNull BaseRevision was, @NotNull BaseRevision become) {
      doModify(was, become);
    }

    @Override
    public void added(@NotNull BaseRevision baseRevision) {
      doModify(baseRevision, baseRevision);
    }

    @Override
    public void removed(@NotNull BaseRevision baseRevision) {
      myScheduler.submit(() -> {
        AbstractVcs vcs = getVcs(baseRevision);
        if (vcs != null) {
          myRevisionsCache.changeRemoved(baseRevision.getPath(), vcs);
        }
        myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(baseRevision.getPath());
      });
    }

    private void doModify(BaseRevision was, BaseRevision become) {
      myScheduler.submit(() -> {
        final AbstractVcs vcs = getVcs(was);
        if (vcs != null) {
          myRevisionsCache.changeUpdated(was.getPath(), vcs);
        }
        myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(become);
      });
    }

    @Nullable
    private AbstractVcs getVcs(@NotNull BaseRevision baseRevision) {
      AbstractVcs vcs = baseRevision.getVcs();
      if (vcs != null) return vcs;
      return myVcsManager.getVcsFor(baseRevision.getFilePath());
    }
  }

  @Override
  public boolean isFreezedWithNotification(@Nls @Nullable String modalTitle) {
    final String freezeReason = isFreezed();
    if (freezeReason == null) return false;

    if (modalTitle != null) {
      Messages.showErrorDialog(project, freezeReason, modalTitle);
    }
    else {
      VcsBalloonProblemNotifier.showOverChangesView(project, freezeReason, MessageType.WARNING);
    }
    return true;
  }

  public void replaceCommitMessage(@NotNull String oldMessage, @NotNull String newMessage) {
    VcsConfiguration.getInstance(project).replaceMessage(oldMessage, newMessage);

    if (areChangeListsEnabled()) {
      for (LocalChangeList changeList : getChangeLists()) {
        if (oldMessage.equals(changeList.getComment())) {
          editComment(changeList.getName(), newMessage);
        }
      }
    }
  }
}
