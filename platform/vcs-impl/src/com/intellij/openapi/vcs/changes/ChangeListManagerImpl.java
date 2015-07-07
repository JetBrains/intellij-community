/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.RuntimeInterruptedException;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictTracker;
import com.intellij.openapi.vcs.changes.ui.CommitHelper;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.impl.*;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.*;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.continuation.ContinuationPause;
import com.intellij.util.messages.Topic;
import com.intellij.vcsUtil.Rethrow;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author max
 */
public class ChangeListManagerImpl extends ChangeListManagerEx implements ProjectComponent, ChangeListOwner, JDOMExternalizable,
                                                                          RoamingTypeDisabled {
  public static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListManagerImpl");
  private static final String EXCLUDED_CONVERTED_TO_IGNORED_OPTION = "EXCLUDED_CONVERTED_TO_IGNORED";

  private final Project myProject;
  private final VcsConfiguration myConfig;
  private final ChangesViewI myChangesViewManager;
  private final FileStatusManager myFileStatusManager;
  private final UpdateRequestsQueue myUpdater;

  private static final AtomicReference<ScheduledExecutorService> ourUpdateAlarm = new AtomicReference<ScheduledExecutorService>();
  static {
    ourUpdateAlarm.set(createChangeListExecutor());
  }

  private static ScheduledThreadPoolExecutor createChangeListExecutor() {
    return VcsUtil.createExecutor("Change List Updater");
  }

  private final Modifier myModifier;

  private FileHolderComposite myComposite;

  private ChangeListWorker myWorker;
  private VcsException myUpdateException = null;
  private Factory<JComponent> myAdditionalInfo;

  private final EventDispatcher<ChangeListListener> myListeners = EventDispatcher.create(ChangeListListener.class);

  private final Object myDataLock = new Object();

  private final List<CommitExecutor> myExecutors = new ArrayList<CommitExecutor>();

  private final IgnoredFilesComponent myIgnoredIdeaLevel;
  private boolean myExcludedConvertedToIgnored;
  private volatile ProgressIndicator myUpdateChangesProgressIndicator = createProgressIndicator();

  public static final Topic<LocalChangeListsLoadedListener> LISTS_LOADED = new Topic<LocalChangeListsLoadedListener>(
    "LOCAL_CHANGE_LISTS_LOADED", LocalChangeListsLoadedListener.class);

  private boolean myShowLocalChangesInvalidated;
  private final AtomicReference<String> myFreezeName;

  // notifies myListeners on the same thread that local changes update is done
  private final DelayedNotificator myDelayedNotificator;

  private final VcsListener myVcsListener = new VcsListener() {
    @Override
    public void directoryMappingChanged() {
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
  };
  private final ChangelistConflictTracker myConflictTracker;
  private VcsDirtyScopeManager myDirtyScopeManager;
  private final VcsDirtyScopeVfsListener myVfsListener;

  private boolean myModalNotificationsBlocked;
  @NotNull private final Collection<LocalChangeList> myListsToBeDeleted = new HashSet<LocalChangeList>();

  public static ChangeListManagerImpl getInstanceImpl(final Project project) {
    return (ChangeListManagerImpl)PeriodicalTasksCloser.getInstance().safeGetComponent(project, ChangeListManager.class);
  }

  void setDirtyScopeManager(VcsDirtyScopeManager dirtyScopeManager) {
    myDirtyScopeManager = dirtyScopeManager;
  }

  public ChangeListManagerImpl(Project project, final VcsConfiguration config) {
    myProject = project;
    myConfig = config;
    myFreezeName = new AtomicReference<String>(null);
    myAdditionalInfo = null;
    myChangesViewManager = myProject.isDefault() ? new DummyChangesView(myProject) : ChangesViewManager.getInstance(myProject);
    myVfsListener = ApplicationManager.getApplication().getComponent(VcsDirtyScopeVfsListener.class);
    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerAdapter() {
      @Override
      public void projectClosing(Project project) {
        myVfsListener.flushDirt();
      }
    });
    myFileStatusManager = FileStatusManager.getInstance(myProject);
    myComposite = new FileHolderComposite(project);
    myIgnoredIdeaLevel = new IgnoredFilesComponent(myProject, true);
    myUpdater = new UpdateRequestsQueue(myProject, ourUpdateAlarm, new ActualUpdater());

    myWorker = new ChangeListWorker(myProject, new MyChangesDeltaForwarder(myProject, ourUpdateAlarm));
    myDelayedNotificator = new DelayedNotificator(myListeners, ourUpdateAlarm);
    myModifier = new Modifier(myWorker, myDelayedNotificator);

    myConflictTracker = new ChangelistConflictTracker(project, this, myFileStatusManager, EditorNotifications.getInstance(project));

    myListeners.addListener(new ChangeListAdapter() {
      @Override
      public void defaultListChanged(final ChangeList oldDefaultList, ChangeList newDefaultList) {
        final LocalChangeList oldList = (LocalChangeList)oldDefaultList;
        if (oldDefaultList == null || oldList.hasDefaultName() || oldDefaultList.equals(newDefaultList)) return;

        if (!ApplicationManager.getApplication().isUnitTestMode() &&
            oldDefaultList.getChanges().isEmpty() &&
            !oldList.isReadOnly()) {

          invokeAfterUpdate(new Runnable() {
            @Override
            public void run() {
              if (getChangeList(oldList.getId()) == null) {
                return; // removed already  
              }
              switch (config.REMOVE_EMPTY_INACTIVE_CHANGELISTS) {
                case SHOW_CONFIRMATION:
                  if (myModalNotificationsBlocked) {
                    myListsToBeDeleted.add(oldList);
                    return;
                  }

                  if (!showRemoveEmptyChangeListsProposal(config, Collections.singletonList(oldList))) {
                    return;
                  }
                  break;
                case DO_NOTHING_SILENTLY:
                  return;
                case DO_ACTION_SILENTLY:
                  break;
              }
              removeChangeList(oldList);
            }
          }, InvokeAfterUpdateMode.SILENT, null, null);
        }
      }
    });
  }

  /**
   * Shows the proposal to delete one or more changelists that were default and became empty.
   *
   * @return true if the changelists have to be deleted, false if not.
   */
  private boolean showRemoveEmptyChangeListsProposal(@NotNull final VcsConfiguration config, @NotNull Collection<LocalChangeList> lists) {
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
                               StringUtil.join(lists, new Function<LocalChangeList, String>() {
                                 @Override
                                 public String fun(LocalChangeList list) {
                                   return StringUtil.first(list.getName(), 30, true);
                                 }
                               }, "<br/>"));
    }

    VcsConfirmationDialog dialog = new VcsConfirmationDialog(myProject, new VcsShowConfirmationOption() {
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
    if (myListsToBeDeleted.isEmpty()) {
      return;
    }
    if (showRemoveEmptyChangeListsProposal(myConfig, myListsToBeDeleted)) {
      for (LocalChangeList list : myListsToBeDeleted) {
        removeChangeList(list);
      }
    }
    myListsToBeDeleted.clear();
  }

  @Override
  public void projectOpened() {
    initializeForNewProject();

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myUpdater.initialized();
      vcsManager.addVcsListener(myVcsListener);
    }
    else {
      ((ProjectLevelVcsManagerImpl)vcsManager).addInitializationRequest(
        VcsInitObject.CHANGE_LIST_MANAGER, new DumbAwareRunnable() {
          @Override
          public void run() {
            myUpdater.initialized();
            broadcastStateAfterLoad();
            vcsManager.addVcsListener(myVcsListener);
          }
        });

      myConflictTracker.startTracking();
    }
  }

  private void broadcastStateAfterLoad() {
    final List<LocalChangeList> listCopy;
    synchronized (myDataLock) {
      listCopy = getChangeListsCopy();
    }
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(LISTS_LOADED).processLoadedLists(listCopy);
    }
  }

  private void initializeForNewProject() {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        synchronized (myDataLock) {
          if (myWorker.isEmpty()) {
            final LocalChangeList list = myWorker.addChangeList(VcsBundle.message("changes.default.changelist.name"), null, null);
            setDefaultChangeList(list);

            if (myIgnoredIdeaLevel.isEmpty()) {
              for (String path : predefinedIgnorePaths()) {
                myIgnoredIdeaLevel.add(IgnoredBeanFactory.ignoreFile(path, myProject));
              }
            }
          }
          if (!Registry.is("ide.hide.excluded.files") && !myExcludedConvertedToIgnored) {
            convertExcludedToIgnored();
            myExcludedConvertedToIgnored = true;
          }
        }
      }
    });
  }

  @NotNull
  List<String> predefinedIgnorePaths() {
    List<String> myIgnoredIdeaLevel = new ArrayList<String>();
    myIgnoredIdeaLevel.add(myProject.getName() + WorkspaceFileType.DOT_DEFAULT_EXTENSION);
    myIgnoredIdeaLevel.add(Project.DIRECTORY_STORE_FOLDER + "/workspace.xml");

    return myIgnoredIdeaLevel;
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
    ProjectLevelVcsManager.getInstance(myProject).removeVcsListener(myVcsListener);

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

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  /**
   * update itself might produce actions done on AWT thread (invoked-after),
   * so waiting for its completion on AWT thread is not good runnable is invoked on AWT thread
   */
  @Override
  public void invokeAfterUpdate(final Runnable afterUpdate,
                                final InvokeAfterUpdateMode mode,
                                @Nullable final String title,
                                @Nullable final ModalityState state) {
    myUpdater.invokeAfterUpdate(afterUpdate, mode, title, null, state);
  }

  @Override
  public void invokeAfterUpdate(final Runnable afterUpdate, final InvokeAfterUpdateMode mode, final String title,
                                final Consumer<VcsDirtyScopeManager> dirtyScopeManagerFiller, final ModalityState state) {
    myUpdater.invokeAfterUpdate(afterUpdate, mode, title, dirtyScopeManagerFiller, state);
  }

  static class DisposedException extends RuntimeException {}

  @Override
  public void freeze(final ContinuationPause context, final String reason) {
    myUpdater.setIgnoreBackgroundOperation(true);
    // this update is nessesary for git, to refresh local changes before
    invokeAfterUpdate(new Runnable() {
      @Override
      public void run() {
        freezeImmediately(reason);
        context.ping();
      }
    }, InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED, "", ModalityState.NON_MODAL);
    context.suspend();
  }

  @Override
  public void freezeImmediately(@Nullable String reason) {
    myUpdater.setIgnoreBackgroundOperation(false);
    myUpdater.pause();
    myUpdateChangesProgressIndicator.cancel();
    myFreezeName.set(reason);
  }

  @Override
  public void letGo() {
    myUpdater.go();
    myFreezeName.set(null);
  }

  @Override
  public String isFreezed() {
    return myFreezeName.get();
  }

  @Override
  public void scheduleUpdate() {
    myUpdater.schedule();
  }

  @Override
  public void scheduleUpdate(boolean updateUnversionedFiles) {
    myUpdater.schedule();
  }

  private class ActualUpdater implements Runnable {
    @Override
    public void run() {
      updateImmediately();
    }
  }

  private void filterOutIgnoredFiles(final List<VcsDirtyScope> scopes) {
    final Set<VirtualFile> refreshFiles = new HashSet<VirtualFile>();
    try {
      synchronized (myDataLock) {
        final IgnoredFilesHolder fileHolder = (IgnoredFilesHolder)myComposite.get(FileHolder.HolderType.IGNORED);

        for (Iterator<VcsDirtyScope> iterator = scopes.iterator(); iterator.hasNext(); ) {
          final VcsModifiableDirtyScope scope = (VcsModifiableDirtyScope)iterator.next();
          final VcsDirtyScopeModifier modifier = scope.getModifier();
          if (modifier != null) {
            fileHolder.notifyVcsStarted(scope.getVcs());
            final Iterator<FilePath> filesIterator = modifier.getDirtyFilesIterator();
            while (filesIterator.hasNext()) {
              final FilePath dirtyFile = filesIterator.next();
              if ((dirtyFile.getVirtualFile() != null) && isIgnoredFile(dirtyFile.getVirtualFile())) {
                filesIterator.remove();
                fileHolder.addFile(dirtyFile.getVirtualFile());
                refreshFiles.add(dirtyFile.getVirtualFile());
              }
            }
            final Collection<VirtualFile> roots = modifier.getAffectedVcsRoots();
            for (VirtualFile root : roots) {
              final Iterator<FilePath> dirIterator = modifier.getDirtyDirectoriesIterator(root);
              while (dirIterator.hasNext()) {
                final FilePath dir = dirIterator.next();
                if ((dir.getVirtualFile() != null) && isIgnoredFile(dir.getVirtualFile())) {
                  dirIterator.remove();
                  fileHolder.addFile(dir.getVirtualFile());
                  refreshFiles.add(dir.getVirtualFile());
                }
              }
            }
            modifier.recheckDirtyKeys();
            if (scope.isEmpty()) {
              iterator.remove();
            }
          }
        }
      }
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
    catch (AssertionError ex) {
      LOG.error(ex);
    }
    for (VirtualFile file : refreshFiles) {
      myFileStatusManager.fileStatusChanged(file);
    }
  }

  private void updateImmediately() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (!vcsManager.hasActiveVcss()) return;

    final VcsInvalidated invalidated = myDirtyScopeManager.retrieveScopes();
    if (checkScopeIsEmpty(invalidated)) return;

    final boolean wasEverythingDirty = invalidated.isEverythingDirty();
    final List<VcsDirtyScope> scopes = invalidated.getScopes();

    try {
      checkIfDisposed();

      // copy existsing data to objects that would be updated.
      // mark for "modifier" that update started (it would create duplicates of modification commands done by user during update;
      // after update of copies of objects is complete, it would apply the same modifications to copies.)
      final DataHolder dataHolder;
      synchronized (myDataLock) {
        dataHolder = new DataHolder((FileHolderComposite)myComposite.copy(), myWorker.copy(), wasEverythingDirty);
        myModifier.enterUpdate();
        if (wasEverythingDirty) {
          myUpdateException = null;
          myAdditionalInfo = null;
        }
      }
      final String scopeInString = (!LOG.isDebugEnabled()) ? "" : StringUtil.join(scopes, new Function<VcsDirtyScope, String>() {
        @Override
        public String fun(VcsDirtyScope scope) {
          return scope.toString();
        }
      }, "->\n");
      LOG.debug("refresh procedure started, everything = " + wasEverythingDirty + " dirty scope: " + scopeInString);
      dataHolder.notifyStart();
      myChangesViewManager.scheduleRefresh();

      myUpdateChangesProgressIndicator = createProgressIndicator();

      iterateScopes(dataHolder, scopes, wasEverythingDirty);

      final boolean takeChanges = (myUpdateException == null);
      if (takeChanges) {
        // update IDEA-level ignored files
        updateIgnoredFiles(dataHolder.getComposite());
      }

      clearCurrentRevisionsCache(invalidated);
      // for the case of project being closed we need a read action here -> to be more consistent
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          if (myProject.isDisposed()) {
            return;
          }
          synchronized (myDataLock) {
            // do same modifications to change lists as was done during update + do delayed notifications
            dataHolder.notifyEnd();
            // should be applied for notifications to be delivered (they were delayed) - anyway whether we take changes or not
            myModifier.finishUpdate(dataHolder.getChangeListWorker());
            // update member from copy
            if (takeChanges) {
              final ChangeListWorker oldWorker = myWorker;
              myWorker = dataHolder.getChangeListWorker();
              myWorker.onAfterWorkerSwitch(oldWorker);
              myModifier.setWorker(myWorker);
              LOG.debug("refresh procedure finished, unversioned size: " +
                        dataHolder.getComposite().getVFHolder(FileHolder.HolderType.UNVERSIONED).getSize() + "\n changes: " + myWorker);
              final boolean statusChanged = !myComposite.equals(dataHolder.getComposite());
              myComposite = dataHolder.getComposite();
              if (statusChanged) {
                myDelayedNotificator.getProxyDispatcher().unchangedFileStatusChanged();
              }
            }
            myShowLocalChangesInvalidated = false;
          }
        }
      });

      for (VcsDirtyScope scope : scopes) {
        AbstractVcs vcs = scope.getVcs();
        if (vcs != null && vcs.isTrackingUnchangedContent()) {
          scope.iterateExistingInsideScope(new Processor<VirtualFile>() {
            @Override
            public boolean process(VirtualFile file) {
              LastUnchangedContentTracker.markUntouched(file); //todo what if it has become dirty again during update?
              return true;
            }
          });
        }
      }


      myChangesViewManager.scheduleRefresh();
    }
    catch (DisposedException e) {
      // OK, we're finishing all the stuff now.
    }
    catch (ProcessCanceledException e) {
      // OK, we're finishing all the stuff now.
    }
    catch (RuntimeInterruptedException ignore) {
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
    catch (AssertionError ex) {
      LOG.error(ex);
    }
    finally {
      myDirtyScopeManager.changesProcessed();

      synchronized (myDataLock) {
        myDelayedNotificator.getProxyDispatcher().changeListUpdateDone();
        myChangesViewManager.scheduleRefresh();
      }
    }
  }

  private boolean checkScopeIsAllIgnored(VcsInvalidated invalidated) {
    if (!invalidated.isEverythingDirty()) {
      filterOutIgnoredFiles(invalidated.getScopes());
      if (invalidated.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private boolean checkScopeIsEmpty(VcsInvalidated invalidated) {
    if (invalidated == null || invalidated.isEmpty()) {
      // a hack here; but otherwise everything here should be refactored ;)
      if (invalidated != null && invalidated.isEmpty() && invalidated.isEverythingDirty()) {
        VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      }
      return true;
    }
    return checkScopeIsAllIgnored(invalidated);
  }

  private void iterateScopes(DataHolder dataHolder, List<VcsDirtyScope> scopes, boolean wasEverythingDirty) {
    final ChangeListManagerGate gate = dataHolder.getChangeListWorker().createSelfGate();
    // do actual requests about file statuses
    Getter<Boolean> disposedGetter = new Getter<Boolean>() {
      @Override
      public Boolean get() {
        return myProject.isDisposed() || myUpdater.getIsStoppedGetter().get();
      }
    };
    final UpdatingChangeListBuilder builder = new UpdatingChangeListBuilder(dataHolder.getChangeListWorker(),
                                                                            dataHolder.getComposite(), disposedGetter, myIgnoredIdeaLevel,
                                                                            gate);

    for (final VcsDirtyScope scope : scopes) {
      myUpdateChangesProgressIndicator.checkCanceled();

      final AbstractVcs vcs = scope.getVcs();
      if (vcs == null) continue;
      scope.setWasEverythingDirty(wasEverythingDirty);
      final VcsModifiableDirtyScope adjustedScope = vcs.adjustDirtyScope((VcsModifiableDirtyScope)scope);

      myChangesViewManager.setBusy(true);
      dataHolder.notifyStartProcessingChanges(adjustedScope);

      actualUpdate(builder, adjustedScope, vcs, dataHolder, gate);

      if (myUpdateException != null) break;
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
    final FileHolderComposite myComposite;
    final ChangeListWorker myChangeListWorker;

    private DataHolder(FileHolderComposite composite, ChangeListWorker changeListWorker, boolean wasEverythingDirty) {
      myComposite = composite;
      myChangeListWorker = changeListWorker;
      myWasEverythingDirty = wasEverythingDirty;
    }

    public void notifyStart() {
      if (myWasEverythingDirty) {
        myComposite.cleanAll();
        myChangeListWorker.notifyStartProcessingChanges(null);
      }
    }

    public void notifyStartProcessingChanges(@NotNull final VcsModifiableDirtyScope scope) {
      if (!myWasEverythingDirty) {
        myComposite.cleanAndAdjustScope(scope);
        myChangeListWorker.notifyStartProcessingChanges(scope);
      }

      myComposite.notifyVcsStarted(scope.getVcs());
      myChangeListWorker.notifyVcsStarted(scope.getVcs());
    }

    public void notifyDoneProcessingChanges() {
      if (!myWasEverythingDirty) {
        myChangeListWorker.notifyDoneProcessingChanges(myDelayedNotificator.getProxyDispatcher());
      }
    }

    public void notifyEnd() {
      if (myWasEverythingDirty) {
        myChangeListWorker.notifyDoneProcessingChanges(myDelayedNotificator.getProxyDispatcher());
      }
    }

    public FileHolderComposite getComposite() {
      return myComposite;
    }

    public ChangeListWorker getChangeListWorker() {
      return myChangeListWorker;
    }
  }

  private void actualUpdate(final UpdatingChangeListBuilder builder, final VcsDirtyScope scope, final AbstractVcs vcs,
                            final DataHolder dataHolder, final ChangeListManagerGate gate) {
    try {
      final ChangeProvider changeProvider = vcs.getChangeProvider();
      if (changeProvider != null) {
        final FoldersCutDownWorker foldersCutDownWorker = new FoldersCutDownWorker();
        try {
          builder.setCurrent(scope, foldersCutDownWorker);
          changeProvider.getChanges(scope, builder, myUpdateChangesProgressIndicator, gate);
        }
        catch (final VcsException e) {
          handleUpdateException(e);
        }
      }
    }
    catch (ProcessCanceledException ignore) {
    }
    catch (Throwable t) {
      LOG.debug(t);
      Rethrow.reThrowRuntime(t);
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
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ((VcsConnectionProblem)e).attemptQuickFix(false);
        }
      });
    }

    if (myUpdateException == null) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
        if (helper instanceof AbstractVcsHelperImpl && ((AbstractVcsHelperImpl)helper).handleCustom(e)) {
          return;
        }
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
      myUpdateException = e;
    }
  }

  private void checkIfDisposed() {
    if (myUpdater.isStopped()) throw new DisposedException();
  }

  public static boolean isUnder(final Change change, final VcsDirtyScope scope) {
    final ContentRevision before = change.getBeforeRevision();
    final ContentRevision after = change.getAfterRevision();
    return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
  }

  @Override
  public List<LocalChangeList> getChangeListsCopy() {
    synchronized (myDataLock) {
      return myWorker.getListsCopy();
    }
  }

  /**
   * @deprecated this method made equivalent to {@link #getChangeListsCopy()} so to don't be confused by method name,
   * better use {@link #getChangeListsCopy()}
   */
  @Override
  @NotNull
  public List<LocalChangeList> getChangeLists() {
    synchronized (myDataLock) {
      return getChangeListsCopy();
    }
  }

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

  public List<VirtualFile> getUnversionedFiles() {
    synchronized (myDataLock) {
      return myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).getFiles();
    }
  }

  Couple<Integer> getUnversionedFilesSize() {
    synchronized (myDataLock) {
      final VirtualFileHolder holder = myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED);
      return Couple.of(holder.getSize(), holder.getNumDirs());
    }
  }

  @Override
  public List<VirtualFile> getModifiedWithoutEditing() {
    synchronized (myDataLock) {
      return myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).getFiles();
    }
  }

  /**
   * @return only roots for ignored folders, and ignored files
   */
  List<VirtualFile> getIgnoredFiles() {
    synchronized (myDataLock) {
      return new ArrayList<VirtualFile>(myComposite.getIgnoredFileHolder().values());
    }
  }

  public List<VirtualFile> getLockedFolders() {
    synchronized (myDataLock) {
      return myComposite.getVFHolder(FileHolder.HolderType.LOCKED).getFiles();
    }
  }

  Map<VirtualFile, LogicalLock> getLogicallyLockedFolders() {
    synchronized (myDataLock) {
      return new HashMap<VirtualFile, LogicalLock>(
        ((LogicallyLockedHolder)myComposite.get(FileHolder.HolderType.LOGICALLY_LOCKED)).getMap());
    }
  }

  public boolean isLogicallyLocked(final VirtualFile file) {
    synchronized (myDataLock) {
      return ((LogicallyLockedHolder)myComposite.get(FileHolder.HolderType.LOGICALLY_LOCKED)).containsKey(file);
    }
  }

  public boolean isContainedInLocallyDeleted(final FilePath filePath) {
    synchronized (myDataLock) {
      return myWorker.isContainedInLocallyDeleted(filePath);
    }
  }

  public List<LocallyDeletedChange> getDeletedFiles() {
    synchronized (myDataLock) {
      return myWorker.getLocallyDeleted().getFiles();
    }
  }

  MultiMap<String, VirtualFile> getSwitchedFilesMap() {
    synchronized (myDataLock) {
      return myWorker.getSwitchedHolder().getBranchToFileMap();
    }
  }

  @Nullable
  Map<VirtualFile, String> getSwitchedRoots() {
    synchronized (myDataLock) {
      return ((SwitchedFileHolder)myComposite.get(FileHolder.HolderType.ROOT_SWITCH)).getFilesMapCopy();
    }
  }

  public VcsException getUpdateException() {
    synchronized (myDataLock) {
      return myUpdateException;
    }
  }

  public Factory<JComponent> getAdditionalUpdateInfo() {
    synchronized (myDataLock) {
      return myAdditionalInfo;
    }
  }

  @Override
  public boolean isFileAffected(final VirtualFile file) {
    synchronized (myDataLock) {
      return myWorker.getStatus(file) != null;
    }
  }

  @Override
  @Nullable
  public LocalChangeList findChangeList(final String name) {
    synchronized (myDataLock) {
      return myWorker.getCopyByName(name);
    }
  }

  @Override
  public LocalChangeList getChangeList(String id) {
    synchronized (myDataLock) {
      return myWorker.getChangeList(id);
    }
  }

  @Override
  public LocalChangeList addChangeList(@NotNull final String name, @Nullable final String comment) {
    return addChangeList(name, comment, null);
  }

  @Override
  public LocalChangeList addChangeList(@NotNull final String name, @Nullable final String comment, @Nullable final Object data) {
    return ApplicationManager.getApplication().runReadAction(new Computable<LocalChangeList>() {
      @Override
      public LocalChangeList compute() {
        synchronized (myDataLock) {
          final LocalChangeList changeList = myModifier.addChangeList(name, comment, data);
          myChangesViewManager.scheduleRefresh();
          return changeList;
        }
      }
    });
  }


  @Override
  public void removeChangeList(final String name) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        synchronized (myDataLock) {
          myModifier.removeChangeList(name);
          myChangesViewManager.scheduleRefresh();
        }
      }
    });
  }

  @Override
  public void removeChangeList(LocalChangeList list) {
    removeChangeList(list.getName());
  }

  /**
   * does no modification to change lists, only notification is sent
   */
  @Override
  @NotNull
  public Runnable prepareForChangeDeletion(final Collection<Change> changes) {
    final Map<String, LocalChangeList> lists = new HashMap<String, LocalChangeList>();
    final Map<String, List<Change>> map;
    synchronized (myDataLock) {
      map = myWorker.listsForChanges(changes, lists);
    }
    return new Runnable() {
      @Override
      public void run() {
        final ChangeListListener multicaster = myDelayedNotificator.getProxyDispatcher();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            synchronized (myDataLock) {
              for (Map.Entry<String, List<Change>> entry : map.entrySet()) {
                final List<Change> changes = entry.getValue();
                for (Iterator<Change> iterator = changes.iterator(); iterator.hasNext(); ) {
                  final Change change = iterator.next();
                  if (getChangeList(change) != null) {
                    // was not actually rolled back
                    iterator.remove();
                  }
                }
                multicaster.changesRemoved(changes, lists.get(entry.getKey()));
              }
              for (String listName : map.keySet()) {
                final LocalChangeList byName = myWorker.getCopyByName(listName);
                if (byName != null && byName.getChanges().isEmpty() && !byName.isDefault() && !byName.isReadOnly()) {
                  myWorker.removeChangeList(listName);
                }
              }
            }
          }
        });
      }
    };
  }

  @Override
  public void setDefaultChangeList(@NotNull final LocalChangeList list) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        synchronized (myDataLock) {
          myModifier.setDefault(list.getName());
        }
      }
    });
    myChangesViewManager.scheduleRefresh();
  }

  @Override
  @Nullable
  public LocalChangeList getDefaultChangeList() {
    synchronized (myDataLock) {
      return myWorker.getDefaultListCopy();
    }
  }

  @Override
  public boolean isDefaultChangeList(ChangeList list) {
    return list instanceof LocalChangeList && myWorker.isDefaultList((LocalChangeList)list);
  }

  @Override
  @NotNull
  public Collection<LocalChangeList> getInvolvedListsFilterChanges(final Collection<Change> changes, final List<Change> validChanges) {
    synchronized (myDataLock) {
      return myWorker.getInvolvedListsFilterChanges(changes, validChanges);
    }
  }

  @Override
  @Nullable
  public LocalChangeList getChangeList(@NotNull Change change) {
    synchronized (myDataLock) {
      return myWorker.listForChange(change);
    }
  }

  @Override
  public String getChangeListNameIfOnlyOne(final Change[] changes) {
    synchronized (myDataLock) {
      return myWorker.listNameIfOnlyOne(changes);
    }
  }

  /**
   * @deprecated better use normal comparison, with equals
   */
  @Override
  @Nullable
  public LocalChangeList getIdentityChangeList(Change change) {
    synchronized (myDataLock) {
      final List<LocalChangeList> lists = myWorker.getListsCopy();
      for (LocalChangeList list : lists) {
        for (Change oldChange : list.getChanges()) {
          if (oldChange == change) {
            return list;
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
    synchronized (myDataLock) {
      final LocalChangeList list = myWorker.getListCopy(file);
      if (list != null) {
        for (Change change : list.getChanges()) {
          final ContentRevision afterRevision = change.getAfterRevision();
          if (afterRevision != null) {
            String revisionPath = FileUtil.toSystemIndependentName(afterRevision.getFile().getIOFile().getPath());
            if (FileUtil.pathsEqual(revisionPath, file.getPath())) return change;
          }
          final ContentRevision beforeRevision = change.getBeforeRevision();
          if (beforeRevision != null) {
            String revisionPath = FileUtil.toSystemIndependentName(beforeRevision.getFile().getIOFile().getPath());
            if (FileUtil.pathsEqual(revisionPath, file.getPath())) return change;
          }
        }
      }

      return null;
    }
  }

  @Override
  public LocalChangeList getChangeList(@NotNull VirtualFile file) {
    synchronized (myDataLock) {
      return myWorker.getListCopy(file);
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
  public FileStatus getStatus(VirtualFile file) {
    synchronized (myDataLock) {
      if (myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).containsFile(file)) return FileStatus.UNKNOWN;
      if (myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).containsFile(file)) return FileStatus.HIJACKED;
      if (myComposite.getIgnoredFileHolder().containsFile(file)) return FileStatus.IGNORED;

      final boolean switched = myWorker.isSwitched(file);
      final FileStatus status = myWorker.getStatus(file);
      if (status != null) {
        return FileStatus.NOT_CHANGED.equals(status) && switched ? FileStatus.SWITCHED : status;
      }
      if (switched) return FileStatus.SWITCHED;
      return FileStatus.NOT_CHANGED;
    }
  }

  @Override
  @NotNull
  public Collection<Change> getChangesIn(VirtualFile dir) {
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
  public Collection<Change> getChangesIn(final FilePath dirPath) {
    synchronized (myDataLock) {
      return myWorker.getChangesIn(dirPath);
    }
  }

  @Override
  public void moveChangesTo(final LocalChangeList list, final Change... changes) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        synchronized (myDataLock) {
          myModifier.moveChangesTo(list.getName(), changes);
        }
      }
    });
    myChangesViewManager.scheduleRefresh();
  }

  @Override
  public void addUnversionedFiles(final LocalChangeList list, @NotNull final List<VirtualFile> files) {
    addUnversionedFiles(list, files, new Condition<FileStatus>() {
      @Override
      public boolean value(FileStatus status) {
        return status == FileStatus.UNKNOWN;
      }
    });
  }

  // TODO this is for quick-fix for GitAdd problem. To be removed after proper fix
  // (which should introduce something like VcsAddRemoveEnvironment)
  @Deprecated
  public void addUnversionedFiles(final LocalChangeList list, @NotNull final List<VirtualFile> files,
                                  final Condition<FileStatus> statusChecker) {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    final Set<VirtualFile> allProcessedFiles = new HashSet<VirtualFile>();
    ChangesUtil.processVirtualFilesByVcs(myProject, files, new ChangesUtil.PerVcsProcessor<VirtualFile>() {
      @Override
      public void process(final AbstractVcs vcs, final List<VirtualFile> items) {
        final CheckinEnvironment environment = vcs.getCheckinEnvironment();
        if (environment != null) {
          final Set<VirtualFile> descendants = getUnversionedDescendantsRecursively(items, statusChecker);
          Set<VirtualFile> parents =
            vcs.areDirectoriesVersionedItems() ? getUnversionedParents(items, statusChecker) : Collections.<VirtualFile>emptySet();

          // it is assumed that not-added parents of files passed to scheduleUnversionedFilesForAddition() will also be added to vcs
          // (inside the method) - so common add logic just needs to refresh statuses of parents
          final List<VcsException> result = ContainerUtil.newArrayList();
          ProgressManager.getInstance().run(new Task.Modal(myProject, "Adding files to VCS...", true) {
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
      }
    });

    if (exceptions.size() > 0) {
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

    if (!list.isDefault()) {
      // find the changes for the added files and move them to the necessary changelist
      invokeAfterUpdate(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              synchronized (myDataLock) {
                List<Change> changesToMove = new ArrayList<Change>();
                final LocalChangeList defaultList = getDefaultChangeList();
                for (Change change : defaultList.getChanges()) {
                  final ContentRevision afterRevision = change.getAfterRevision();
                  if (afterRevision != null) {
                    VirtualFile vFile = afterRevision.getFile().getVirtualFile();
                    if (allProcessedFiles.contains(vFile)) {
                      changesToMove.add(change);
                    }
                  }
                }

                if (changesToMove.size() > 0) {
                  moveChangesTo(list, changesToMove.toArray(new Change[changesToMove.size()]));
                }
              }
            }
          });

          myChangesViewManager.scheduleRefresh();
        }
      }, InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE_NOT_AWT, VcsBundle.message("change.lists.manager.add.unversioned"), null);
    }
    else {
      myChangesViewManager.scheduleRefresh();
    }
  }

  @NotNull
  private Set<VirtualFile> getUnversionedDescendantsRecursively(@NotNull List<VirtualFile> items,
                                                                @NotNull final Condition<FileStatus> condition) {
    final Set<VirtualFile> result = ContainerUtil.newHashSet();
    Processor<VirtualFile> addToResultProcessor = new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile file) {
        if (condition.value(getStatus(file))) {
          result.add(file);
        }
        return true;
      }
    };

    for (VirtualFile item : items) {
      VcsRootIterator.iterateVfUnderVcsRoot(myProject, item, addToResultProcessor);
    }

    return result;
  }

  @NotNull
  private Set<VirtualFile> getUnversionedParents(@NotNull Collection<VirtualFile> items, @NotNull Condition<FileStatus> condition) {
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
  public void addChangeListListener(ChangeListListener listener) {
    myListeners.addListener(listener);
  }


  @Override
  public void removeChangeListListener(ChangeListListener listener) {
    myListeners.removeListener(listener);
  }

  @Override
  public void registerCommitExecutor(CommitExecutor executor) {
    myExecutors.add(executor);
  }

  @Override
  public void commitChanges(LocalChangeList changeList, List<Change> changes) {
    doCommit(changeList, changes, false);
  }

  private boolean doCommit(final LocalChangeList changeList, final List<Change> changes, final boolean synchronously) {
    FileDocumentManager.getInstance().saveAllDocuments();
    return new CommitHelper(myProject, changeList, changes, changeList.getName(),
                            StringUtil.isEmpty(changeList.getComment()) ? changeList.getName() : changeList.getComment(),
                            new ArrayList<CheckinHandler>(), false, synchronously, FunctionUtil.nullConstant(), null).doCommit();
  }

  @Override
  public void commitChangesSynchronously(LocalChangeList changeList, List<Change> changes) {
    doCommit(changeList, changes, true);
  }

  @Override
  public boolean commitChangesSynchronouslyWithResult(final LocalChangeList changeList, final List<Change> changes) {
    return doCommit(changeList, changes, true);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public void readExternal(Element element) throws InvalidDataException {
    if (!myProject.isDefault()) {
      synchronized (myDataLock) {
        myIgnoredIdeaLevel.clear();
        new ChangeListManagerSerialization(myIgnoredIdeaLevel, myWorker).readExternal(element);
        if ((!myWorker.isEmpty()) && getDefaultChangeList() == null) {
          setDefaultChangeList(myWorker.getListsCopy().get(0));
        }
      }
      myExcludedConvertedToIgnored = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, EXCLUDED_CONVERTED_TO_IGNORED_OPTION));
      myConflictTracker.loadState(element);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (!myProject.isDefault()) {
      final IgnoredFilesComponent ignoredFilesComponent;
      final ChangeListWorker worker;
      synchronized (myDataLock) {
        ignoredFilesComponent = new IgnoredFilesComponent(myIgnoredIdeaLevel);
        worker = myWorker.copy();
      }
      new ChangeListManagerSerialization(ignoredFilesComponent, worker).writeExternal(element);
      if (myExcludedConvertedToIgnored) {
        JDOMExternalizerUtil.writeField(element, EXCLUDED_CONVERTED_TO_IGNORED_OPTION, String.valueOf(true));
      }
      myConflictTracker.saveState(element);
    }
  }

  // used in TeamCity
  @Override
  public void reopenFiles(List<FilePath> paths) {
    final ReadonlyStatusHandlerImpl readonlyStatusHandler = (ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject);
    final boolean savedOption = readonlyStatusHandler.getState().SHOW_DIALOG;
    readonlyStatusHandler.getState().SHOW_DIALOG = false;
    try {
      readonlyStatusHandler.ensureFilesWritable(collectFiles(paths));
    }
    finally {
      readonlyStatusHandler.getState().SHOW_DIALOG = savedOption;
    }
  }

  @Override
  public List<CommitExecutor> getRegisteredExecutors() {
    return Collections.unmodifiableList(myExecutors);
  }

  private static class MyDirtyFilesScheduler {
    private static final int ourPiecesLimit = 100;
    final List<VirtualFile> myFiles = new ArrayList<VirtualFile>();
    final List<VirtualFile> myDirs = new ArrayList<VirtualFile>();
    private boolean myEveryThing;
    private int myCnt;
    private final Project myProject;

    private MyDirtyFilesScheduler(final Project project) {
      myProject = project;
      myCnt = 0;
      myEveryThing = false;
    }

    public void accept(final Collection<VirtualFile> coll) {
      for (VirtualFile vf : coll) {
        if (myCnt > ourPiecesLimit) {
          myEveryThing = true;
          break;
        }
        if (vf.isDirectory()) {
          myDirs.add(vf);
        }
        else {
          myFiles.add(vf);
        }
        ++myCnt;
      }
    }

    public void arise() {
      final VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
      if (myEveryThing) {
        vcsDirtyScopeManager.markEverythingDirty();
      }
      else {
        vcsDirtyScopeManager.filesDirty(myFiles, myDirs);
      }
    }
  }

  @Override
  public void addFilesToIgnore(final IgnoredFileBean... filesToIgnore) {
    myIgnoredIdeaLevel.add(filesToIgnore);
    scheduleUnversionedUpdate();
  }

  @Override
  public void addDirectoryToIgnoreImplicitly(@NotNull String path) {
    myIgnoredIdeaLevel.addIgnoredDirectoryImplicitly(path, myProject);
  }

  public IgnoredFilesComponent getIgnoredFilesComponent() {
    return myIgnoredIdeaLevel;
  }

  private void scheduleUnversionedUpdate() {
    final MyDirtyFilesScheduler scheduler = new MyDirtyFilesScheduler(myProject);

    synchronized (myDataLock) {
      final VirtualFileHolder unversionedHolder = myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED);
      final IgnoredFilesHolder ignoredHolder = (IgnoredFilesHolder)myComposite.get(FileHolder.HolderType.IGNORED);

      scheduler.accept(unversionedHolder.getFiles());
      scheduler.accept(ignoredHolder.values());
    }

    scheduler.arise();
  }

  @Override
  public void setFilesToIgnore(final IgnoredFileBean... filesToIgnore) {
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

  @Override
  public IgnoredFileBean[] getFilesToIgnore() {
    return myIgnoredIdeaLevel.getFilesToIgnore();
  }

  @Override
  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    return myIgnoredIdeaLevel.isIgnoredFile(file);
  }

  @Override
  @Nullable
  public String getSwitchedBranch(final VirtualFile file) {
    synchronized (myDataLock) {
      return myWorker.getBranchForFile(file);
    }
  }

  @Override
  public String getDefaultListName() {
    synchronized (myDataLock) {
      return myWorker.getDefaultListName();
    }
  }

  private static VirtualFile[] collectFiles(final List<FilePath> paths) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (FilePath path : paths) {
      if (path.getVirtualFile() != null) {
        result.add(path.getVirtualFile());
      }
    }

    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public boolean setReadOnly(final String name, final boolean value) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        synchronized (myDataLock) {
          final boolean result = myModifier.setReadOnly(name, value);
          myChangesViewManager.scheduleRefresh();
          return result;
        }
      }
    });
  }

  @Override
  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        synchronized (myDataLock) {
          final boolean result = myModifier.editName(fromName, toName);
          myChangesViewManager.scheduleRefresh();
          return result;
        }
      }
    });
  }

  @Override
  public String editComment(@NotNull final String fromName, final String newComment) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        synchronized (myDataLock) {
          final String oldComment = myModifier.editComment(fromName, newComment);
          myChangesViewManager.scheduleRefresh();
          return oldComment;
        }
      }
    });
  }

  @TestOnly
  public void waitUntilRefreshed() {
    myVfsListener.flushDirt();
    myUpdater.waitUntilRefreshed();
    waitUpdateAlarm();
  }

  // this is for perforce tests to ensure that LastSuccessfulUpdateTracker receives the event it needs
  private static void waitUpdateAlarm() {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    ourUpdateAlarm.get().execute(new Runnable() {
      @Override
      public void run() {
        semaphore.up();
      }
    });
    semaphore.waitFor();
  }

  public void stopEveryThingIfInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    ourUpdateAlarm.get().shutdownNow();
    ourUpdateAlarm.set(createChangeListExecutor());
  }

  public void forceGoInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myUpdater.forceGo();
  }

  public void executeOnUpdaterThread(Runnable r) {
    ourUpdateAlarm.get().execute(r);
  }

  @Override
  @TestOnly
  public boolean ensureUpToDate(final boolean canBeCanceled) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      updateImmediately();
      return true;
    }
    myVfsListener.flushDirt();
    final EnsureUpToDateFromNonAWTThread worker = new EnsureUpToDateFromNonAWTThread(myProject);
    worker.execute();
    myUpdater.waitUntilRefreshed();
    waitUpdateAlarm();
    return worker.isDone();
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
    private final AtomicReference<ScheduledExecutorService> myService;

    public MyChangesDeltaForwarder(final Project project, final AtomicReference<ScheduledExecutorService> service) {
      myProject = project;
      myService = service;
      myRevisionsCache = RemoteRevisionsCache.getInstance(project);
      myVcsManager = ProjectLevelVcsManager.getInstance(project);
    }

    @Override
    public void modify(final BaseRevision was, final BaseRevision become) {
      myService.get().submit(new Runnable() {
        @Override
        public void run() {
          final AbstractVcs vcs = getVcs(was);
          if (vcs != null) {
            myRevisionsCache.plus(Pair.create(was.getPath().getPath(), vcs));
          }
          // maybe define modify method?
          myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(become);
        }
      });
    }

    @Override
    public void plus(final BaseRevision baseRevision) {
      myService.get().submit(new Runnable() {
        @Override
        public void run() {
          final AbstractVcs vcs = getVcs(baseRevision);
          if (vcs != null) {
            myRevisionsCache.plus(Pair.create(baseRevision.getPath().getPath(), vcs));
          }
          myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(baseRevision);
        }
      });
    }

    @Override
    public void minus(final BaseRevision baseRevision) {
      myService.get().submit(new Runnable() {
        @Override
        public void run() {
          final AbstractVcs vcs = getVcs(baseRevision);
          if (vcs != null) {
            myRevisionsCache.minus(Pair.create(baseRevision.getPath().getPath(), vcs));
          }
          myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(baseRevision.getPath().getPath());
        }
      });
    }

    @Nullable
    private AbstractVcs getVcs(final BaseRevision baseRevision) {
      VcsKey vcsKey = baseRevision.getVcs();
      if (vcsKey == null) {
        FilePath path = baseRevision.getPath();
        vcsKey = findVcs(path);
        if (vcsKey == null) return null;
      }
      return myVcsManager.findVcsByName(vcsKey.getName());
    }

    @Nullable
    private VcsKey findVcs(final FilePath path) {
      // does not matter directory or not
      VirtualFile vf = path.getVirtualFile();
      if (vf == null) {
        vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.getIOFile());
      }
      if (vf == null) return null;
      final AbstractVcs vcs = myVcsManager.getVcsFor(vf);
      return vcs == null ? null : vcs.getKeyInstanceMethod();
    }
  }

  @Override
  public boolean isFreezedWithNotification(String modalTitle) {
    final String freezeReason = isFreezed();
    if (freezeReason != null) {
      if (modalTitle != null) {
        Messages.showErrorDialog(myProject, freezeReason, modalTitle);
      }
      else {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, freezeReason, MessageType.WARNING);
      }
    }
    return freezeReason != null;
  }
}
