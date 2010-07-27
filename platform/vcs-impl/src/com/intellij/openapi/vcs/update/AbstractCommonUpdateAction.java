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
package com.intellij.openapi.vcs.update;

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.errorTreeView.HotfixData;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.AbstractVcsAction;
import com.intellij.openapi.vcs.actions.DescindingFilesFilter;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesAdapter;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.changes.committed.IntoSelfVirtualFileConvertor;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public abstract class AbstractCommonUpdateAction extends AbstractVcsAction {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.update.AbstractCommonUpdateAction");

  private final ActionInfo myActionInfo;
  private final ScopeInfo myScopeInfo;

  protected AbstractCommonUpdateAction(ActionInfo actionInfo, ScopeInfo scopeInfo) {
    myActionInfo = actionInfo;
    myScopeInfo = scopeInfo;
  }

  private String getCompleteActionName(VcsContext dataContext) {
    return myActionInfo.getActionName(myScopeInfo.getScopeName(dataContext, myActionInfo));
  }

  protected void actionPerformed(final VcsContext context) {
    final Project project = context.getProject();

    boolean showUpdateOptions = myActionInfo.showOptions(project);

    if (project != null) {
      try {
        final FilePath[] filePaths = myScopeInfo.getRoots(context, myActionInfo);
        final FilePath[] roots = DescindingFilesFilter.filterDescindingFiles(filterRoots(filePaths, context), project);
        if (roots.length == 0) {
          return;
        }

        final Map<AbstractVcs, Collection<FilePath>> vcsToVirtualFiles = createVcsToFilesMap(roots, project);

        if (showUpdateOptions || OptionsDialog.shiftIsPressed(context.getModifiers())) {
          showOptionsDialog(vcsToVirtualFiles, project, context);
        }

        for (AbstractVcs vcs : vcsToVirtualFiles.keySet()) {
          final UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
          if ((updateEnvironment != null) && (! updateEnvironment.validateOptions(vcsToVirtualFiles.get(vcs)))) {
            // messages already shown
            return;
          }
        }

        if (ApplicationManager.getApplication().isDispatchThread()) {
          ApplicationManager.getApplication().saveAll();
        }
        Task.Backgroundable task = new Updater(project, roots, vcsToVirtualFiles);
        ProgressManager.getInstance().run(task);
      }
      catch (ProcessCanceledException e1) {
        //ignore
      }
    }
  }

  private boolean canGroupByChangelist(final Set<AbstractVcs> abstractVcses) {
    if (myActionInfo.canGroupByChangelist()) {
      for(AbstractVcs vcs: abstractVcses) {
        if (vcs.getCachingCommittedChangesProvider() != null) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean someSessionWasCanceled(List<UpdateSession> updateSessions) {
    for (UpdateSession updateSession : updateSessions) {
      if (updateSession.isCanceled()) {
        return true;
      }
    }
    return false;
  }

  private static String getAllFilesAreUpToDateMessage(FilePath[] roots) {
    if (roots.length == 1 && !roots[0].isDirectory()) {
      return VcsBundle.message("message.text.file.is.up.to.date");
    }
    else {
      return VcsBundle.message("message.text.all.files.are.up.to.date");
    }
  }

  private void showOptionsDialog(final Map<AbstractVcs, Collection<FilePath>> updateEnvToVirtualFiles, final Project project,
                                 final VcsContext dataContext) {
    LinkedHashMap<Configurable, AbstractVcs> envToConfMap = createConfigurableToEnvMap(updateEnvToVirtualFiles);
    if (!envToConfMap.isEmpty()) {
      UpdateOrStatusOptionsDialog dialogOrStatus = myActionInfo.createOptionsDialog(project, envToConfMap,
                                                                                    myScopeInfo.getScopeName(dataContext,
                                                                                                             myActionInfo));
      dialogOrStatus.show();
      if (!dialogOrStatus.isOK()) {
        throw new ProcessCanceledException();
      }
    }
  }

  private LinkedHashMap<Configurable, AbstractVcs> createConfigurableToEnvMap(Map<AbstractVcs, Collection<FilePath>> updateEnvToVirtualFiles) {
    LinkedHashMap<Configurable, AbstractVcs> envToConfMap = new LinkedHashMap<Configurable, AbstractVcs>();
    for (AbstractVcs vcs : updateEnvToVirtualFiles.keySet()) {
      Configurable configurable = myActionInfo.getEnvironment(vcs).createConfigurable(updateEnvToVirtualFiles.get(vcs));
      if (configurable != null) {
        envToConfMap.put(configurable, vcs);
      }
    }
    return envToConfMap;
  }

  private Map<AbstractVcs,Collection<FilePath>> createVcsToFilesMap(FilePath[] roots, Project project) {
    HashMap<AbstractVcs, Collection<FilePath>> resultPrep = new HashMap<AbstractVcs, Collection<FilePath>>();

    for (FilePath file : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      if (vcs != null) {
        UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
        if (updateEnvironment != null) {
          if (!resultPrep.containsKey(vcs)) resultPrep.put(vcs, new HashSet<FilePath>());
          resultPrep.get(vcs).add(file);
        }
      }
    }

    final Map<AbstractVcs, Collection<FilePath>> result = new HashMap<AbstractVcs, Collection<FilePath>>();
    for (Map.Entry<AbstractVcs, Collection<FilePath>> entry : resultPrep.entrySet()) {
      final AbstractVcs vcs = entry.getKey();
      final List<FilePath> paths = new ArrayList<FilePath>(entry.getValue());
      final List<VirtualFile> files = ObjectsConvertor.convert(paths, ObjectsConvertor.FILEPATH_TO_VIRTUAL, ObjectsConvertor.NOT_NULL);
      result.put(vcs, ObjectsConvertor.vf2fp(vcs.filterUniqueRoots(files, IntoSelfVirtualFileConvertor.getInstance())));
    }

    return result;
  }

  private static boolean containsParent(FilePath[] array, FilePath file) {
    for (FilePath virtualFile : array) {
      if (virtualFile == file) continue;
      if (VfsUtil.isAncestor(virtualFile.getIOFile(), file.getIOFile(), false)) return true;
    }
    return false;
  }

  @NotNull
  private FilePath[] filterRoots(FilePath[] roots, VcsContext vcsContext) {
    final ArrayList<FilePath> result = new ArrayList<FilePath>();
    final Project project = vcsContext.getProject();
    for (FilePath file : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      if (vcs != null) {
        if (!myScopeInfo.filterExistsInVcs() || AbstractVcs.fileInVcsByFileStatus(project, file)) {
          UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
          if (updateEnvironment != null) {
            result.add(file);
          }
        }
        else {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null && virtualFile.isDirectory()) {
            final VirtualFile[] vcsRoots = ProjectLevelVcsManager.getInstance(vcsContext.getProject()).getAllVersionedRoots();
            for(VirtualFile vcsRoot: vcsRoots) {
              if (VfsUtil.isAncestor(virtualFile, vcsRoot, false)) {
                result.add(file);
              }
            }
          }
        }
      }
    }
    return result.toArray(new FilePath[result.size()]);
  }

  protected abstract boolean filterRootsBeforeAction();

  protected void update(VcsContext vcsContext, Presentation presentation) {
    Project project = vcsContext.getProject();

    if (project != null) {

      String actionName = getCompleteActionName(vcsContext);
      if (myActionInfo.showOptions(project) || OptionsDialog.shiftIsPressed(vcsContext.getModifiers())) {
        actionName += "...";
      }

      presentation.setText(actionName);

      presentation.setVisible(true);
      presentation.setEnabled(true);

      if (supportingVcsesAreEmpty(vcsContext.getProject(), myActionInfo)) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
      }

      if (filterRootsBeforeAction()) {
        FilePath[] roots = filterRoots(myScopeInfo.getRoots(vcsContext, myActionInfo), vcsContext);
        if (roots.length == 0) {
          presentation.setVisible(false);
          presentation.setEnabled(false);
        }
      }

      if (presentation.isVisible() && presentation.isEnabled() &&
          ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
        presentation.setEnabled(false);
      }
    } else {
      presentation.setVisible(false);
      presentation.setEnabled(false);
    }
 }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  private static boolean supportingVcsesAreEmpty(final Project project, final ActionInfo actionInfo) {
    if (project == null) return true;
    final AbstractVcs[] allActiveVcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for (AbstractVcs activeVcs : allActiveVcss) {
      if (actionInfo.getEnvironment(activeVcs) != null) return false;
    }
    return true;
  }

  private class Updater extends Task.Backgroundable {
    private final Project myProject;
    private final ProjectLevelVcsManagerEx myProjectLevelVcsManager;
    private UpdatedFiles myUpdatedFiles;
    private final FilePath[] myRoots;
    private final Map<AbstractVcs, Collection<FilePath>> myVcsToVirtualFiles;
    private final Map<HotfixData, List<VcsException>> myGroupedExceptions;
    private final List<UpdateSession> myUpdateSessions;
    private int myUpdateNumber;

    // vcs name, context object
    private final Map<AbstractVcs, SequentialUpdatesContext> myContextInfo;
    private final VcsDirtyScopeManager myDirtyScopeManager;

    private Label myBefore;
    private Label myAfter;

    public Updater(final Project project, final FilePath[] roots, final Map<AbstractVcs, Collection<FilePath>> vcsToVirtualFiles) {
      super(project, getTemplatePresentation().getText(), true, VcsConfiguration.getInstance(project).getUpdateOption());
      myProject = project;
      myProjectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project);
      myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
      myRoots = roots;
      myVcsToVirtualFiles = vcsToVirtualFiles;

      myUpdatedFiles = UpdatedFiles.create();
      myGroupedExceptions = new HashMap<HotfixData, List<VcsException>>();
      myUpdateSessions = new ArrayList<UpdateSession>();

      // create from outside without any context; context is created by vcses
      myContextInfo = new HashMap<AbstractVcs, SequentialUpdatesContext>();
      myUpdateNumber = 1;
    }

    private void reset() {
      myUpdatedFiles = UpdatedFiles.create();
      myGroupedExceptions.clear();
      myUpdateSessions.clear();
      ++ myUpdateNumber;
    }

    private void suspendIfNeeded() {
      if (! myActionInfo.canChangeFileStatus()) {
        // i.e. for update but not for integrate or status
        ((VcsDirtyScopeManagerImpl) myDirtyScopeManager).suspendMe();
      }
    }

    private void releaseIfNeeded() {
      if (! myActionInfo.canChangeFileStatus()) {
        // i.e. for update but not for integrate or status
        ((VcsDirtyScopeManagerImpl) myDirtyScopeManager).reanimate();
      }
    }

    public void run(@NotNull final ProgressIndicator indicator) {
      suspendIfNeeded();
      try {
        runImpl(indicator);
      } catch (Throwable t) {
        releaseIfNeeded();
        if (t instanceof Error) {
          throw ((Error) t);
        } else if (t instanceof RuntimeException) {
          throw ((RuntimeException) t);
        }
        throw new RuntimeException(t);
      }
    }

    private void runImpl(@NotNull final ProgressIndicator indicator) {
      ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();
      myProjectLevelVcsManager.startBackgroundVcsOperation();

      myBefore = LocalHistory.getInstance().putSystemLabel(myProject, "Before update");

      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

      try {
        int toBeProcessed = myVcsToVirtualFiles.size();
        int processed = 0;
        for (AbstractVcs vcs : myVcsToVirtualFiles.keySet()) {
          final UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
          updateEnvironment.fillGroups(myUpdatedFiles);
          Collection<FilePath> files = myVcsToVirtualFiles.get(vcs);

          final SequentialUpdatesContext context = myContextInfo.get(vcs);
          final Ref<SequentialUpdatesContext> refContext = new Ref<SequentialUpdatesContext>(context);

          // actual update
          UpdateSession updateSession =
            updateEnvironment.updateDirectories(files.toArray(new FilePath[files.size()]), myUpdatedFiles, progressIndicator, refContext);

          myContextInfo.put(vcs, refContext.get());
          processed++;
          if (progressIndicator != null) {
            progressIndicator.setFraction((double)processed / (double)toBeProcessed);
            progressIndicator.setText2("");
          }
          final List<VcsException> exceptionList = updateSession.getExceptions();
          gatherExceptions(vcs, exceptionList);
          myUpdateSessions.add(updateSession);
        }
      } finally {
        try {
          if (progressIndicator != null) {
            progressIndicator.setText(VcsBundle.message("progress.text.synchronizing.files"));
            progressIndicator.setText2("");
          }
          doVfsRefresh();
        } finally {
          myAfter = LocalHistory.getInstance().putSystemLabel(myProject, "After update");
          myProjectLevelVcsManager.stopBackgroundVcsOperation();
        }
      }
    }

    private void gatherExceptions(final AbstractVcs vcs, final List<VcsException> exceptionList) {
      final VcsExceptionsHotFixer fixer = vcs.getVcsExceptionsHotFixer();
      if (fixer == null) {
        putExceptions(null, exceptionList);
      } else {
        putExceptions(fixer.groupExceptions(ActionType.update, exceptionList));
      }
    }

    private void putExceptions(final Map<HotfixData, List<VcsException>> map) {
      for (Map.Entry<HotfixData, List<VcsException>> entry : map.entrySet()) {
        putExceptions(entry.getKey(), entry.getValue());
      }
    }

    private void putExceptions(final HotfixData key, @NotNull final List<VcsException> list) {
      if (list.isEmpty()) return;
      List<VcsException> exceptionList = myGroupedExceptions.get(key);
      if (exceptionList == null) {
        exceptionList = new ArrayList<VcsException>();
        myGroupedExceptions.put(key, exceptionList);
      }
      exceptionList.addAll(list);
    }

    private void doVfsRefresh() {
      final String actionName = VcsBundle.message("local.history.update.from.vcs");
      final LocalHistoryAction action = LocalHistory.getInstance().startAction(actionName);
      try {
        LOG.info("Calling refresh files after update for roots: " + Arrays.toString(myRoots));
        RefreshVFsSynchronously.updateAllChanged(myUpdatedFiles);
      }
      finally {
        action.finish();
        LocalHistory.getInstance().putSystemLabel(myProject, actionName);
      }
    }

    @Nullable
    public NotificationInfo getNotificationInfo() {
      StringBuffer text = new StringBuffer();
      final List<FileGroup> groups = myUpdatedFiles.getTopLevelGroups();
      for (FileGroup group : groups) {
        appendGroup(text, group);
      }

      return new NotificationInfo("VCS Update", "VCS Update Finished", text.toString(), true);
    }

    private void appendGroup(final StringBuffer text, final FileGroup group) {
      final int s = group.getFiles().size();
      if (s > 0) {
        if (text.length() > 0) text.append("\n");
        text.append(s + " Files " + group.getUpdateName());
      }

      final List<FileGroup> list = group.getChildren();
      for (FileGroup g : list) {
        appendGroup(text, g);
      }
    }

    public void onSuccess() {
      try {
        onSuccessImpl();
      } finally {
        releaseIfNeeded();
      }
    }

    private void onSuccessImpl() {
      if (myProject.isDisposed()) {
        ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
        return;
      }
      boolean continueChain = false;
      for (SequentialUpdatesContext context : myContextInfo.values()) {
        continueChain |= (context != null) && (context.shouldFail());
      }
      final boolean continueChainFinal = continueChain;

      final boolean someSessionWasCancelled = someSessionWasCanceled(myUpdateSessions);
      if (! someSessionWasCancelled) {
        for (final UpdateSession updateSession : myUpdateSessions) {
          updateSession.onRefreshFilesCompleted();
        }
      }

      if (myActionInfo.canChangeFileStatus()) {
        final List<VirtualFile> files = new ArrayList<VirtualFile>();
        final RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
        revisionsCache.invalidate(myUpdatedFiles);
        UpdateFilesHelper.iterateFileGroupFiles(myUpdatedFiles, new UpdateFilesHelper.Callback() {
          public void onFile(final String filePath, final String groupId) {
            @NonNls final String path = VfsUtil.pathToUrl(filePath.replace(File.separatorChar, '/'));
            final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(path);
            if (file != null) {
              files.add(file);
            }
          }
        });
        myDirtyScopeManager.filesDirty(files, null);
      }

      final boolean updateSuccess = (! someSessionWasCancelled) && (myGroupedExceptions.isEmpty());

      if (! someSessionWasCancelled) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProject.isDisposed()) {
              ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
              return;
            }
            if (! myGroupedExceptions.isEmpty()) {
              if (continueChainFinal) {
                gatherContextInterruptedMessages();
              }
              AbstractVcsHelper.getInstance(myProject).showErrors(myGroupedExceptions, VcsBundle.message("message.title.vcs.update.errors",
                                                                                                     getTemplatePresentation().getText()));
            }
            else {
              final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
              if (indicator != null) {
                indicator.setText(VcsBundle.message("progress.text.updating.done"));
              }
            }

            final boolean noMerged = myUpdatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).isEmpty();
            if (myUpdatedFiles.isEmpty() && myGroupedExceptions.isEmpty()) {
              ToolWindowManager.getInstance(myProject).notifyByBalloon(
                ChangesViewContentManager.TOOLWINDOW_ID, MessageType.INFO, getAllFilesAreUpToDateMessage(myRoots));
            }
            else if (! myUpdatedFiles.isEmpty()) {
              showUpdateTree(continueChainFinal && updateSuccess && noMerged);

              final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
              cache.processUpdatedFiles(myUpdatedFiles);
            }

            ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();

            if (continueChainFinal && updateSuccess) {
              if (!noMerged) {
                showContextInterruptedError();
              } else {
                // trigger next update; for CVS when updating from several branvhes simultaneously
                reset();
                ProgressManager.getInstance().run(Updater.this);
              }
            }
          }
        });
      } else if (continueChain) {
        // since error
        showContextInterruptedError();
        ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
      }
    }

    private void showContextInterruptedError() {
      gatherContextInterruptedMessages();
      AbstractVcsHelper.getInstance(myProject).showErrors(myGroupedExceptions,
                                    VcsBundle.message("message.title.vcs.update.errors", getTemplatePresentation().getText()));
    }

    private void gatherContextInterruptedMessages() {
      for (Map.Entry<AbstractVcs, SequentialUpdatesContext> entry : myContextInfo.entrySet()) {
        final SequentialUpdatesContext context = entry.getValue();
        if ((context == null) || (! context.shouldFail())) continue;
        final VcsException exception = new VcsException(context.getMessageWhenInterruptedBeforeStart());
        gatherExceptions(entry.getKey(), Collections.singletonList(exception));
      }
    }

    private void showUpdateTree(final boolean willBeContinued) {
      RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(myProject);
      restoreUpdateTree.registerUpdateInformation(myUpdatedFiles, myActionInfo);
      final String text = getTemplatePresentation().getText() + ((willBeContinued || (myUpdateNumber > 1)) ? ("#" + myUpdateNumber) : "");
      final UpdateInfoTree updateInfoTree = myProjectLevelVcsManager.showUpdateProjectInfo(myUpdatedFiles, text, myActionInfo);

      updateInfoTree.setBefore(myBefore);
      updateInfoTree.setAfter(myAfter);
      
      // todo make temporal listener of changes reload
      if (updateInfoTree != null) {
        updateInfoTree.setCanGroupByChangeList(canGroupByChangelist(myVcsToVirtualFiles.keySet()));
        final MessageBusConnection messageBusConnection = myProject.getMessageBus().connect();
        messageBusConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesAdapter() {
          public void incomingChangesUpdated(final List<CommittedChangeList> receivedChanges) {
            if (receivedChanges != null) {
              updateInfoTree.setChangeLists(receivedChanges);
              messageBusConnection.disconnect();
            }
          }
        });
      }
    }

    public void onCancel() {
      onSuccess();
    }
  }
}
