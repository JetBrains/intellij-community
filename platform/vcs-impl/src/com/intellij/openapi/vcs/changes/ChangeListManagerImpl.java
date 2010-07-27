/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.lifecycle.AtomicSectionsAware;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictTracker;
import com.intellij.openapi.vcs.changes.ui.CommitHelper;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.Topic;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author max
 */
public class ChangeListManagerImpl extends ChangeListManagerEx implements ProjectComponent, ChangeListOwner, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListManagerImpl");

  private final Project myProject;
  private final ChangesViewManager myChangesViewManager;
  private final FileStatusManager myFileStatusManager;
  private final UpdateRequestsQueue myUpdater;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private static final ScheduledExecutorService ourUpdateAlarm = ConcurrencyUtil.newSingleScheduledThreadExecutor("Change List Updater", Thread.MIN_PRIORITY + 1);

  private final Modifier myModifier;

  private FileHolderComposite myComposite;

  private final ChangeListWorker myWorker;
  private VcsException myUpdateException = null;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private final EventDispatcher<ChangeListListener> myListeners = EventDispatcher.create(ChangeListListener.class);

  private final Object myDataLock = new Object();

  private final List<CommitExecutor> myExecutors = new ArrayList<CommitExecutor>();

  private final IgnoredFilesComponent myIgnoredIdeaLevel;
  private ProgressIndicator myUpdateChangesProgressIndicator;

  public static final Key<Object> DOCUMENT_BEING_COMMITTED_KEY = new Key<Object>("DOCUMENT_BEING_COMMITTED");

  public static final Topic<LocalChangeListsLoadedListener> LISTS_LOADED = new Topic<LocalChangeListsLoadedListener>(
    "LOCAL_CHANGE_LISTS_LOADED", LocalChangeListsLoadedListener.class);

  private boolean myShowLocalChangesInvalidated;

  private final DelayedNotificator myDelayedNotificator;

  private final VcsListener myVcsListener = new VcsListener() {
    public void directoryMappingChanged() {
      VcsDirtyScopeManager.getInstanceChecked(myProject).markEverythingDirty();
    }
  };
  private final ChangelistConflictTracker myConflictTracker;

  public static ChangeListManagerImpl getInstanceImpl(final Project project) {
    return (ChangeListManagerImpl) project.getComponent(ChangeListManager.class);
  }

  public ChangeListManagerImpl(Project project, final VcsConfiguration config) {
    myProject = project;
    myChangesViewManager = ChangesViewManager.getInstance(myProject);
    myFileStatusManager = FileStatusManager.getInstance(myProject);
    myComposite = new FileHolderComposite(project);
    myIgnoredIdeaLevel = new IgnoredFilesComponent(myProject);
    myUpdater = new UpdateRequestsQueue(myProject, ourUpdateAlarm, new ActualUpdater());

    myWorker = new ChangeListWorker(myProject, new MyChangesDeltaForwarder(myProject, ourUpdateAlarm));
    myDelayedNotificator = new DelayedNotificator(myListeners, ourUpdateAlarm);
    myModifier = new Modifier(myWorker, myDelayedNotificator);

    myConflictTracker = new ChangelistConflictTracker(project, this, myFileStatusManager, EditorNotifications.getInstance(project));

    myListeners.addListener(new ChangeListAdapter() {
      @Override
      public void defaultListChanged(final ChangeList oldDefaultList, ChangeList newDefaultList) {
        if (((LocalChangeList)oldDefaultList).hasDefaultName()) return;
        if (!ApplicationManager.getApplication().isUnitTestMode() &&
          oldDefaultList instanceof LocalChangeList &&
          oldDefaultList.getChanges().isEmpty() &&
          !((LocalChangeList)oldDefaultList).isReadOnly()) {

          invokeAfterUpdate(new Runnable() {
            public void run() {
              if (getChangeList(((LocalChangeList)oldDefaultList).getId()) == null) {
                return; // removed already  
              }
              switch (config.REMOVE_EMPTY_INACTIVE_CHANGELISTS) {

                case SHOW_CONFIRMATION:
                  VcsConfirmationDialog dialog = new VcsConfirmationDialog(myProject, new VcsShowConfirmationOption() {
                    public Value getValue() {
                      return config.REMOVE_EMPTY_INACTIVE_CHANGELISTS;
                    }

                    public void setValue(Value value) {
                      config.REMOVE_EMPTY_INACTIVE_CHANGELISTS = value;
                    }

                    @Override
                    public boolean isPersistent() {
                      return true;
                    }
                  }, "<html>The empty changelist '" + StringUtil.first(oldDefaultList.getName(), 30, true) + "' is no longer active.<br>" +
                     "Do you want to remove it?</html>", "&Remember my choice");
                  dialog.show();
                  if (!dialog.isOK()) {
                    return;
                  }
                  break;
                case DO_NOTHING_SILENTLY:
                  return;
                case DO_ACTION_SILENTLY:
                  break;
              }
              removeChangeList((LocalChangeList)oldDefaultList);
            }
          }, InvokeAfterUpdateMode.SILENT, null, null);
        }
      }
    });
  }

  public void projectOpened() {
    initializeForNewProject();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myWorker.initialized();
      myUpdater.initialized();
      ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
    }
    else {
      ((ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject)).addInitializationRequest(
        VcsInitObject.CHANGE_LIST_MANAGER, new DumbAwareRunnable() {
        public void run() {
          myWorker.initialized();
          myUpdater.initialized();
          broadcastStateAfterLoad();
          ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
        }
      });
    }

    myConflictTracker.startTracking();
  }

  private void broadcastStateAfterLoad() {
    final List<LocalChangeList> listCopy;
    synchronized (myDataLock) {
      listCopy = getChangeListsCopy();
    }
    if (! listCopy.isEmpty()) {
      myProject.getMessageBus().syncPublisher(LISTS_LOADED).processLoadedLists(listCopy);
    }
  }

  private void initializeForNewProject() {
    synchronized (myDataLock) {
      if (myWorker.isEmpty()) {
        final LocalChangeList list = myWorker.addChangeList(VcsBundle.message("changes.default.changlist.name"), null);
        setDefaultChangeList(list);

        if (myIgnoredIdeaLevel.isEmpty()) {
          final String name = myProject.getName();
          myIgnoredIdeaLevel.add(IgnoredBeanFactory.ignoreFile(name + WorkspaceFileType.DOT_DEFAULT_EXTENSION, myProject));
          myIgnoredIdeaLevel.add(IgnoredBeanFactory.ignoreFile(Project.DIRECTORY_STORE_FOLDER + "/workspace.xml", myProject));
        }
      }
    }
  }

  public void projectClosed() {
    ProjectLevelVcsManager.getInstance(myProject).removeVcsListener(myVcsListener);

    synchronized (myDataLock) {
      if (myUpdateChangesProgressIndicator != null) {
        myUpdateChangesProgressIndicator.cancel();
      }
    }

    myUpdater.stop();
    myConflictTracker.stopTracking();
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "ChangeListManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  /**
   * update itself might produce actions done on AWT thread (invoked-after),
   * so waiting for its completion on AWT thread is not good
   *
   * runnable is invoked on AWT thread
   */
  public void invokeAfterUpdate(final Runnable afterUpdate, final InvokeAfterUpdateMode mode, final String title, final ModalityState state) {
    myUpdater.invokeAfterUpdate(afterUpdate, mode, title, null, state);
  }

  public void invokeAfterUpdate(final Runnable afterUpdate, final InvokeAfterUpdateMode mode, final String title,
                                final Consumer<VcsDirtyScopeManager> dirtyScopeManagerFiller, final ModalityState state) {
    myUpdater.invokeAfterUpdate(afterUpdate, mode, title, dirtyScopeManagerFiller, state);
  }

  static class DisposedException extends RuntimeException {}

  public void scheduleUpdate() {
    myUpdater.schedule(true);
  }

  public void scheduleUpdate(boolean updateUnversionedFiles) {
    myUpdater.schedule(updateUnversionedFiles);
  }

  private class ActualUpdater implements LocalChangesUpdater {
    public void execute(boolean updateUnversioned, AtomicSectionsAware atomicSectionsAware) {
      updateImmediately(updateUnversioned, atomicSectionsAware);
    }
  }

  private void updateImmediately(final boolean updateUnversionedFiles, final AtomicSectionsAware atomicSectionsAware) {
    FileHolderComposite composite;
    ChangeListWorker changeListWorker;

    final VcsDirtyScopeManagerImpl dirtyScopeManager;
    try {
      dirtyScopeManager = ((VcsDirtyScopeManagerImpl) VcsDirtyScopeManager.getInstanceChecked(myProject));
    }
    catch(ProcessCanceledException ex) {
      return;
    }
    catch(Exception ex) {
      LOG.error(ex);
      return;
    }
    final VcsInvalidated invalidated = dirtyScopeManager.retrieveScopes();
    if (invalidated == null || invalidated.isEmpty()) {
      // a hack here; but otherwise everything here should be refactored ;)
      if (invalidated != null && invalidated.isEmpty() && invalidated.isEverythingDirty()) {
        VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      }
      return;
    }
    final boolean wasEverythingDirty = invalidated.isEverythingDirty();
    final List<VcsDirtyScope> scopes = invalidated.getScopes();

    try {
      checkIfDisposed();

      // copy existsing data to objects that would be updated.
      // mark for "modifier" that update started (it would create duplicates of modification commands done by user during update;
      // after update of copies of objects is complete, it would apply the same modifications to copies.)
      synchronized (myDataLock) {
        changeListWorker = myWorker.copy();
        composite = updateUnversionedFiles ? (FileHolderComposite) myComposite.copy() : myComposite;
        myModifier.enterUpdate();
        if (wasEverythingDirty) {
          myUpdateException = null;
        }
        if (updateUnversionedFiles && wasEverythingDirty) {
          composite.cleanAll();         
        }
      }
      if (wasEverythingDirty) {
        changeListWorker.notifyStartProcessingChanges(null);
      }
      myChangesViewManager.scheduleRefresh();

      final ChangeListManagerGate gate = changeListWorker.createSelfGate();

      // do actual requests about file statuses
      final UpdatingChangeListBuilder builder = new UpdatingChangeListBuilder(changeListWorker, composite, new Getter<Boolean>() {
        public Boolean get() {
          return myUpdater.isStopped();
        }
      }, updateUnversionedFiles, myIgnoredIdeaLevel, gate);

      myUpdateChangesProgressIndicator = new EmptyProgressIndicator() {
        @Override
        public boolean isCanceled() {
          return myUpdater.isStopped() || atomicSectionsAware.shouldExitAsap();
        }
        @Override
        public void checkCanceled() {
          checkIfDisposed();
          atomicSectionsAware.checkShouldExit();
        }
      };
      for (final VcsDirtyScope scope : scopes) {
        atomicSectionsAware.checkShouldExit();

        final AbstractVcs vcs = scope.getVcs();
        if (vcs == null) continue;
        final VcsAppendableDirtyScope adjustedScope = vcs.adjustDirtyScope((VcsAppendableDirtyScope) scope);

        myChangesViewManager.updateProgressText(VcsBundle.message("changes.update.progress.message", vcs.getDisplayName()), false);
        if (! wasEverythingDirty) {
          changeListWorker.notifyStartProcessingChanges(adjustedScope);
        }
        if (updateUnversionedFiles && !wasEverythingDirty) {
          composite.cleanScope(adjustedScope);
        }
        
        try {
          actualUpdate(wasEverythingDirty, composite, builder, adjustedScope, vcs, changeListWorker, gate);
        }
        catch (Throwable t) {
          LOG.info(t);
          if (t instanceof Error) {
            throw (Error) t;
          } else if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
          }
          throw new RuntimeException(t);
        }

        if (myUpdateException != null) break;
      }

      final boolean takeChanges = (myUpdateException == null);
      
      synchronized (myDataLock) {
        // do same modifications to change lists as was done during update + do delayed notifications
        if (wasEverythingDirty) {
          changeListWorker.notifyDoneProcessingChanges(myDelayedNotificator.getProxyDispatcher());
        }
        myModifier.exitUpdate();
        // should be applied for notifications to be delivered (they were delayed)
        myModifier.apply(changeListWorker);
        myModifier.clearQueue();
        // update member from copy
        if (takeChanges) {
          myWorker.takeData(changeListWorker);
        }

        if (takeChanges && updateUnversionedFiles) {
          boolean statusChanged = !myComposite.equals(composite);
          myComposite = composite;
          if (statusChanged) {
            myDelayedNotificator.getProxyDispatcher().unchangedFileStatusChanged();
          }
        }

        if (takeChanges) {
          updateIgnoredFiles(false);
        }
        myShowLocalChangesInvalidated = false;
      }
      myChangesViewManager.scheduleRefresh();
    }
    catch (DisposedException e) {
      // OK, we're finishing all the stuff now.
    }
    catch(ProcessCanceledException e) {
      // OK, we're finishing all the stuff now.
    }
    catch(Exception ex) {
      LOG.error(ex);
    }
    catch(AssertionError ex) {
      LOG.error(ex);
    }
    finally {
      dirtyScopeManager.changesProcessed();
      
      synchronized (myDataLock) {
        myDelayedNotificator.getProxyDispatcher().changeListUpdateDone();
        myChangesViewManager.scheduleRefresh();
      }
    }
  }

  private void actualUpdate(final boolean wasEverythingDirty, final FileHolderComposite composite, final UpdatingChangeListBuilder builder,
                            final VcsDirtyScope scope, final AbstractVcs vcs, final ChangeListWorker changeListWorker,
                            final ChangeListManagerGate gate) {
    try {
      final ChangeProvider changeProvider = vcs.getChangeProvider();
      if (changeProvider != null) {
        final FoldersCutDownWorker foldersCutDownWorker = new FoldersCutDownWorker();
        try {
          builder.setCurrent(scope, foldersCutDownWorker);
          changeProvider.getChanges(scope, builder, myUpdateChangesProgressIndicator, gate);
        }
        catch (VcsException e) {
          LOG.info(e);
          if (myUpdateException == null) {
            myUpdateException = e;
          }
        }
        composite.getIgnoredFileHolder().calculateChildren();
      }
    }
    finally {
      if ((! myUpdater.isStopped()) && !wasEverythingDirty) {
        changeListWorker.notifyDoneProcessingChanges(myDelayedNotificator.getProxyDispatcher());
      }
    }
  }

  private void checkIfDisposed() {
    if (myUpdater.isStopped()) throw new DisposedException();
  }

  static boolean isUnder(final Change change, final VcsDirtyScope scope) {
    final ContentRevision before = change.getBeforeRevision();
    final ContentRevision after = change.getAfterRevision();
    return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
  }

  public List<LocalChangeList> getChangeListsCopy() {
    synchronized (myDataLock) {
      return myWorker.getListsCopy();
    }
  }

  /**
   * @deprecated 
   * this method made equivalent to {@link #getChangeListsCopy()} so to don't be confused by method name,
   * better use {@link #getChangeListsCopy()}
   */
  @NotNull
  public List<LocalChangeList> getChangeLists() {
    synchronized (myDataLock) {
      return getChangeListsCopy();
    }
  }

  public List<File> getAffectedPaths() {
    synchronized (myDataLock) {
      return myWorker.getAffectedPaths();
    }
  }

  @NotNull
  public List<VirtualFile> getAffectedFiles() {
    synchronized (myDataLock) {
      return myWorker.getAffectedFiles();
    }
  }

  public List<VirtualFile> getUnversionedFiles() {
    return myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).getFiles();
  }

  Pair<Integer, Integer> getUnversionedFilesSize() {
    final VirtualFileHolder holder = myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED);
    return new Pair<Integer, Integer>(holder.getSize(), holder.getNumDirs());
  }

  List<VirtualFile> getModifiedWithoutEditing() {
    return new ArrayList<VirtualFile>(myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).getFiles());
  }

  /**
   * @return only roots for ignored folders, and ignored files
   */
  List<VirtualFile> getIgnoredFiles() {
    return new ArrayList<VirtualFile>(myComposite.getIgnoredFileHolder().getBranchToFileMap().values());
  }

  public List<VirtualFile> getLockedFolders() {
    return new ArrayList<VirtualFile>(myComposite.getVFHolder(FileHolder.HolderType.LOCKED).getFiles());
  }

  Map<VirtualFile, LogicalLock> getLogicallyLockedFolders() {
    return new HashMap<VirtualFile, LogicalLock>(((LogicallyLockedHolder) myComposite.get(FileHolder.HolderType.LOGICALLY_LOCKED)).getMap());
  }

  public boolean isLogicallyLocked(final VirtualFile file) {
    return ((LogicallyLockedHolder) myComposite.get(FileHolder.HolderType.LOGICALLY_LOCKED)).getMap().containsKey(file);
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
      return ((SwitchedFileHolder) myComposite.get(FileHolder.HolderType.ROOT_SWITCH)).getFilesMapCopy();
    }
  }

  public VcsException getUpdateException() {
    return myUpdateException;
  }

  public boolean isFileAffected(final VirtualFile file) {
    synchronized (myDataLock) {
      return myWorker.getStatus(file) != null;
    }
  }

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

  public LocalChangeList addChangeList(@NotNull String name, final String comment) {
    synchronized (myDataLock) {
      final LocalChangeList changeList = myModifier.addChangeList(name, comment);
      myChangesViewManager.scheduleRefresh();
      return changeList;
    }
  }

  public void removeChangeList(final String name) {
    synchronized (myDataLock) {
      myModifier.removeChangeList(name);
      myChangesViewManager.scheduleRefresh();
    }
  }

  public void removeChangeList(LocalChangeList list) {
    removeChangeList(list.getName());
  }

  /**
   * does no modification to change lists, only notification is sent
   */
  @NotNull
  public Runnable prepareForChangeDeletion(final Collection<Change> changes) {
    final Map<String, LocalChangeList> lists = new HashMap<String, LocalChangeList>();
    final Map<String, List<Change>> map;
    synchronized (myDataLock) {
      map = myWorker.listsForChanges(changes, lists);
    }
    return new Runnable() {
      public void run() {
        final ChangeListListener multicaster = myDelayedNotificator.getProxyDispatcher();
        synchronized (myDataLock) {
          for (Map.Entry<String, List<Change>> entry : map.entrySet()) {
            final List<Change> changes = entry.getValue();
            for (Iterator<Change> iterator = changes.iterator(); iterator.hasNext();) {
              final Change change = iterator.next();
              if (getChangeList(change) != null) {
                // was not actually rolled back
                iterator.remove();
              }
            }
            multicaster.changesRemoved(changes, lists.get(entry.getKey()));
          }
        }
      }
    };
  }

  public void setDefaultChangeList(@NotNull LocalChangeList list) {
    synchronized (myDataLock) {
      myModifier.setDefault(list.getName());
      myChangesViewManager.scheduleRefresh();
    }
  }

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

  @NotNull
  public Collection<LocalChangeList> getInvolvedListsFilterChanges(final Collection<Change> changes, final List<Change> validChanges) {
    synchronized (myDataLock) {
      return myWorker.getInvolvedListsFilterChanges(changes, validChanges);
    }
  }

  @Nullable
  public LocalChangeList getChangeList(Change change) {
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
   * @deprecated
   * better use normal comparison, with equals
   */
  @Nullable
  public LocalChangeList getIdentityChangeList(Change change) {
    synchronized (myDataLock) {
      final List<LocalChangeList> lists = myWorker.getListsCopy();
      for (LocalChangeList list : lists) {
        for(Change oldChange: list.getChanges()) {
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

  @Nullable
  public Change getChange(final FilePath file) {
    synchronized (myDataLock) {
      return myWorker.getChangeForPath(file);
    }
  }

  public boolean isUnversioned(VirtualFile file) {
    return myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).containsFile(file);
  }

  @NotNull
  public FileStatus getStatus(VirtualFile file) {
    synchronized (myDataLock) {
      if (myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).containsFile(file)) return FileStatus.UNKNOWN;
      if (myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).containsFile(file)) return FileStatus.HIJACKED;
      if (myComposite.getIgnoredFileHolder().containsFile(file)) return FileStatus.IGNORED;

      final FileStatus status = myWorker.getStatus(file);
      if (status != null) {
        return status;
      }
      if (myWorker.isSwitched(file)) return FileStatus.SWITCHED;
      return FileStatus.NOT_CHANGED;
    }
  }

  @NotNull
  public Collection<Change> getChangesIn(VirtualFile dir) {
    return getChangesIn(new FilePathImpl(dir));
  }

  @NotNull
  public Collection<Change> getChangesIn(final FilePath dirPath) {
    synchronized (myDataLock) {
      return myWorker.getChangesIn(dirPath);
    }
  }

  public void moveChangesTo(LocalChangeList list, final Change[] changes) {
    synchronized (myDataLock) {
      myModifier.moveChangesTo(list.getName(), changes);
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myChangesViewManager.refreshView();
      }
    });
  }

  public void addUnversionedFiles(final LocalChangeList list, @NotNull final List<VirtualFile> files) {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    ChangesUtil.processVirtualFilesByVcs(myProject, files, new ChangesUtil.PerVcsProcessor<VirtualFile>() {
      public void process(final AbstractVcs vcs, final List<VirtualFile> items) {
        final CheckinEnvironment environment = vcs.getCheckinEnvironment();
        if (environment != null) {
          final List<VcsException> result = environment.scheduleUnversionedFilesForAddition(items);
          if (result != null) {
            exceptions.addAll(result);
          }
        }
      }
    });

    if (exceptions.size() > 0) {
      StringBuilder message = new StringBuilder(VcsBundle.message("error.adding.files.prompt"));
      for(VcsException ex: exceptions) {
        message.append("\n").append(ex.getMessage());
      }
      Messages.showErrorDialog(myProject, message.toString(), VcsBundle.message("error.adding.files.title"));
    }

    for (VirtualFile file : files) {
      myFileStatusManager.fileStatusChanged(file);
    }
    VcsDirtyScopeManager.getInstance(myProject).filesDirty(files, null);

    if (!list.isDefault()) {
      // find the changes for the added files and move them to the necessary changelist
      invokeAfterUpdate(new Runnable() {
        public void run() {
          synchronized (myDataLock) {
            List<Change> changesToMove = new ArrayList<Change>();
            final LocalChangeList defaultList = getDefaultChangeList();
            for(Change change: defaultList.getChanges()) {
              final ContentRevision afterRevision = change.getAfterRevision();
              if (afterRevision != null) {
                VirtualFile vFile = afterRevision.getFile().getVirtualFile();
                if (files.contains(vFile)) {
                  changesToMove.add(change);
                }
              }
            }

            if (changesToMove.size() > 0) {
              moveChangesTo(list, changesToMove.toArray(new Change[changesToMove.size()]));
            }
          }

          myChangesViewManager.scheduleRefresh();
        }
      },  InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE_NOT_AWT, VcsBundle.message("change.lists.manager.add.unversioned"), null);
    } else {
      myChangesViewManager.scheduleRefresh();
    }
  }

  public Project getProject() {
    return myProject;
  }

  public void addChangeListListener(ChangeListListener listener) {
    myListeners.addListener(listener);
  }


  public void removeChangeListListener(ChangeListListener listener) {
    myListeners.removeListener(listener);
  }

  public void registerCommitExecutor(CommitExecutor executor) {
    myExecutors.add(executor);
  }

  public void commitChanges(LocalChangeList changeList, List<Change> changes) {
    doCommit(changeList, changes, false);
  }

  private boolean doCommit(final LocalChangeList changeList, final List<Change> changes, final boolean synchronously) {
    return new CommitHelper(myProject, changeList, changes, changeList.getName(),
                     changeList.getComment(), new ArrayList<CheckinHandler>(), false, synchronously, null).doCommit();
  }

  public void commitChangesSynchronously(LocalChangeList changeList, List<Change> changes) {
    doCommit(changeList, changes, true);
  }

  public boolean commitChangesSynchronouslyWithResult(final LocalChangeList changeList, final List<Change> changes) {
    return doCommit(changeList, changes, true);
  }

  @SuppressWarnings({"unchecked"})
  public void readExternal(Element element) throws InvalidDataException {
    if (! myProject.isDefault()) {
      synchronized (myDataLock) {
        myIgnoredIdeaLevel.clear();
        new ChangeListManagerSerialization(myIgnoredIdeaLevel, myWorker).readExternal(element);
        if ((! myWorker.isEmpty()) && getDefaultChangeList() == null) {
          setDefaultChangeList(myWorker.getListsCopy().get(0));
        }
      }
      myConflictTracker.loadState(element);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (! myProject.isDefault()) {
      final IgnoredFilesComponent ignoredFilesComponent;
      final ChangeListWorker worker;
      synchronized (myDataLock) {
        ignoredFilesComponent = new IgnoredFilesComponent(myProject);
        ignoredFilesComponent.add(myIgnoredIdeaLevel.getFilesToIgnore());
        worker = myWorker.copy();
      }
      new ChangeListManagerSerialization(ignoredFilesComponent, worker).writeExternal(element);
      myConflictTracker.saveState(element);
    }
  }

  // used in TeamCity
  public void reopenFiles(List<FilePath> paths) {
    final ReadonlyStatusHandlerImpl readonlyStatusHandler = (ReadonlyStatusHandlerImpl)ReadonlyStatusHandlerImpl.getInstance(myProject);
    final boolean savedOption = readonlyStatusHandler.getState().SHOW_DIALOG;
    readonlyStatusHandler.getState().SHOW_DIALOG = false;
    try {
      readonlyStatusHandler.ensureFilesWritable(collectFiles(paths));
    }
    finally {
      readonlyStatusHandler.getState().SHOW_DIALOG = savedOption;
    }
  }

  public List<CommitExecutor> getRegisteredExecutors() {
    return Collections.unmodifiableList(myExecutors);
  }

  public void addFilesToIgnore(final IgnoredFileBean... filesToIgnore) {
    myIgnoredIdeaLevel.add(filesToIgnore);
    updateIgnoredFiles(true);
  }

  public void setFilesToIgnore(final IgnoredFileBean... filesToIgnore) {
    myIgnoredIdeaLevel.set(filesToIgnore);
    updateIgnoredFiles(true);
  }

  private void updateIgnoredFiles(final boolean checkIgnored) {
    synchronized (myDataLock) {
      List<VirtualFile> unversionedFiles = myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).getFiles();
      //List<VirtualFile> ignoredFiles = myComposite.getVFHolder(FileHolder.HolderType.IGNORED).getFiles();
      boolean somethingChanged = false;
      for(VirtualFile file: unversionedFiles) {
        if (isIgnoredFile(file)) {
          somethingChanged = true;
          myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).removeFile(file);
          myComposite.getIgnoredFileHolder().addFile(file, "", false);
        }
      }
      /*if (checkIgnored) {
        for(VirtualFile file: ignoredFiles) {
          if (!isIgnoredFile(file)) {
            somethingChanged = true;
            // the file may have been reported as ignored by the VCS, so we can't directly move it to unversioned files
            VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
          }
        }
      }*/
      if (somethingChanged) {
        myFileStatusManager.fileStatusesChanged();
        myChangesViewManager.scheduleRefresh();
      }
    }
  }

  public IgnoredFileBean[] getFilesToIgnore() {
    return myIgnoredIdeaLevel.getFilesToIgnore();
  }

  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    return myIgnoredIdeaLevel.isIgnoredFile(file);
  }

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

    return VfsUtil.toVirtualFileArray(result);
  }

  public boolean setReadOnly(final String name, final boolean value) {
    synchronized (myDataLock) {
      final boolean result = myModifier.setReadOnly(name, value);
      myChangesViewManager.scheduleRefresh();
      return result;
    }
  }

  public boolean editName(@NotNull final String fromName, @NotNull final String toName) {
    synchronized (myDataLock) {
      final boolean result = myModifier.editName(fromName, toName);
      myChangesViewManager.scheduleRefresh();
      return result;
    }
  }

  public String editComment(@NotNull final String fromName, final String newComment) {
    synchronized (myDataLock) {
      final String oldComment = myModifier.editComment(fromName, newComment);
      myChangesViewManager.scheduleRefresh();
      return oldComment;
    }
  }

  /**
   * Can be called only from not AWT thread; to do smthg after ChangeListManager refresh, call invokeAfterUpdate
   */
  public boolean ensureUpToDate(final boolean canBeCanceled) {
    final EnsureUpToDateFromNonAWTThread worker = new EnsureUpToDateFromNonAWTThread(myProject);
    worker.execute();
    return worker.isDone();
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

  private static class MyChangesDeltaForwarder implements PlusMinus<Pair<String, AbstractVcs>> {
    //private SlowlyClosingAlarm myAlarm;
    private RemoteRevisionsCache myRevisionsCache;
    private final ProjectLevelVcsManager myVcsManager;
    private ExecutorWrapper myExecutorWrapper;
    private final ExecutorService myService;


    public MyChangesDeltaForwarder(final Project project, final ExecutorService service) {
      myService = service;
      //myAlarm = ControlledAlarmFactory.createOnSharedThread(project, UpdateRequestsQueue.LOCAL_CHANGES_UPDATE, service);
      myExecutorWrapper = new ExecutorWrapper(project, UpdateRequestsQueue.LOCAL_CHANGES_UPDATE);
      myRevisionsCache = RemoteRevisionsCache.getInstance(project);
      myVcsManager = ProjectLevelVcsManager.getInstance(project);
    }

    public void plus(final Pair<String, AbstractVcs> stringAbstractVcsPair) {
      final Pair<String, AbstractVcs> correctedPair = getCorrectedPair(stringAbstractVcsPair);
      if (correctedPair == null) return;
      myService.submit(new Runnable() {
        public void run() {
          myExecutorWrapper.submit(new Consumer<AtomicSectionsAware>() {
            public void consume(AtomicSectionsAware atomicSectionsAware) {
              myRevisionsCache.plus(correctedPair);
            }
          });
        }
      });
    }

    public void minus(final Pair<String, AbstractVcs> stringAbstractVcsPair) {
      final Pair<String, AbstractVcs> correctedPair = getCorrectedPair(stringAbstractVcsPair);
      if (correctedPair == null) return;
      myService.submit(new Runnable() {
        public void run() {
          myExecutorWrapper.submit(new Consumer<AtomicSectionsAware>() {
            public void consume(AtomicSectionsAware atomicSectionsAware) {
              myRevisionsCache.minus(correctedPair);
            }
          });
          myRevisionsCache.minus(correctedPair);
        }
      });
    }

    @Nullable
    private Pair<String, AbstractVcs> getCorrectedPair(final Pair<String, AbstractVcs> stringAbstractVcsPair) {
      Pair<String, AbstractVcs> correctedPair = stringAbstractVcsPair;
      if (stringAbstractVcsPair.getSecond() == null) {
        final String path = stringAbstractVcsPair.getFirst();
        final VcsKey vcsKey = findVcs(path);
        if (vcsKey == null) return null;
        correctedPair = new Pair<String, AbstractVcs>(path, myVcsManager.findVcsByName(vcsKey.getName()));
      }
      return correctedPair;
    }

    @Nullable
    private VcsKey findVcs(final String path) {
      // does not matter directory or not
      final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
      if (vf == null) return null;
      final AbstractVcs vcs = myVcsManager.getVcsFor(vf);
      return vcs == null ? null : vcs.getKeyInstanceMethod();
    }
  }
}
