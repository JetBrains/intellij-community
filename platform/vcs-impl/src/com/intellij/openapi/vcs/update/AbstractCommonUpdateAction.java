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
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.AbstractVcsAction;
import com.intellij.openapi.vcs.actions.DescindingFilesFilter;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesAdapter;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public abstract class AbstractCommonUpdateAction extends AbstractVcsAction {
  private final boolean myAlwaysVisible;
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.update.AbstractCommonUpdateAction");

  private final ActionInfo myActionInfo;
  private final ScopeInfo myScopeInfo;

  protected AbstractCommonUpdateAction(ActionInfo actionInfo, ScopeInfo scopeInfo, boolean alwaysVisible) {
    myActionInfo = actionInfo;
    myScopeInfo = scopeInfo;
    myAlwaysVisible = alwaysVisible;
  }

  private String getCompleteActionName(VcsContext dataContext) {
    return myActionInfo.getActionName(myScopeInfo.getScopeName(dataContext, myActionInfo));
  }

  protected void actionPerformed(final VcsContext context) {
    final Project project = context.getProject();

    boolean showUpdateOptions = myActionInfo.showOptions(project);

    LOG.debug(String.format("project: %s, show update options: %s", project, showUpdateOptions));

    if (project != null) {
      try {
        final FilePath[] filePaths = myScopeInfo.getRoots(context, myActionInfo);
        final FilePath[] roots = DescindingFilesFilter.filterDescindingFiles(filterRoots(filePaths, context), project);
        if (roots.length == 0) {
          LOG.debug("No roots found.");
          return;
        }

        final Map<AbstractVcs, Collection<FilePath>> vcsToVirtualFiles = createVcsToFilesMap(roots, project);

        for (AbstractVcs vcs : vcsToVirtualFiles.keySet()) {
          final UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
          if ((updateEnvironment != null) && (! updateEnvironment.validateOptions(vcsToVirtualFiles.get(vcs)))) {
            // messages already shown
            LOG.debug("Options not valid for files: " + vcsToVirtualFiles);
            return;
          }
        }

        if (showUpdateOptions || OptionsDialog.shiftIsPressed(context.getModifiers())) {
          showOptionsDialog(vcsToVirtualFiles, project, context);
        }

        if (ApplicationManager.getApplication().isDispatchThread()) {
          ApplicationManager.getApplication().saveAll();
        }
        Task.Backgroundable task = new Updater(project, roots, vcsToVirtualFiles);
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          task.run(new EmptyProgressIndicator());
        }
        else {
          ProgressManager.getInstance().run(task);
        }
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
    LOG.debug("configurables map: " + envToConfMap);
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
      result.put(vcs, vcs.filterUniqueRoots(paths, ObjectsConvertor.FILEPATH_TO_VIRTUAL));
    }

    return result;
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
      final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
      final boolean underVcs = vcsManager.hasActiveVcss();
      if (! underVcs) {
        presentation.setVisible(false);
        return;
      }

      String actionName = getCompleteActionName(vcsContext);
      if (myActionInfo.showOptions(project) || OptionsDialog.shiftIsPressed(vcsContext.getModifiers())) {
        actionName += "...";
      }

      presentation.setText(actionName);

      presentation.setVisible(true);
      presentation.setEnabled(true);

      if (supportingVcsesAreEmpty(vcsManager, myActionInfo)) {
        presentation.setVisible(myAlwaysVisible);
        presentation.setEnabled(false);
        return;
      }

      if (filterRootsBeforeAction()) {
        FilePath[] roots = filterRoots(myScopeInfo.getRoots(vcsContext, myActionInfo), vcsContext);
        if (roots.length == 0) {
          presentation.setVisible(myAlwaysVisible);
          presentation.setEnabled(false);
          return;
        }
      }

      if (presentation.isVisible() && presentation.isEnabled() &&
          vcsManager.isBackgroundVcsOperationRunning()) {
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

  private static boolean supportingVcsesAreEmpty(final ProjectLevelVcsManager vcsManager, final ActionInfo actionInfo) {
    final AbstractVcs[] allActiveVcss = vcsManager.getAllActiveVcss();
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
        runImpl();
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

    private void runImpl() {
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
          ProgressManager.progress(VcsBundle.message("progress.text.synchronizing.files"));
          doVfsRefresh();
        } finally {
          myProjectLevelVcsManager.stopBackgroundVcsOperation();
          if (!myProject.isDisposed()) {
            myProject.getMessageBus().syncPublisher(UpdatedFilesListener.UPDATED_FILES).
              consume(UpdatedFilesReverseSide.getPathsFromUpdatedFiles(myUpdatedFiles));
          }
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
        notifyAnnotations();
      }
      finally {
        action.finish();
        if ((! myProject.isOpen()) || myProject.isDisposed()) {
          LocalHistory.getInstance().putSystemLabel(myProject, actionName);
        }
      }
    }

    private void notifyAnnotations() {
      final VcsAnnotationRefresher refresher = myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED);
      UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(myUpdatedFiles, new UpdateFilesHelper.Callback() {
        @Override
        public void onFile(String filePath, String groupId) {
          refresher.dirty(filePath);
        }
      });
    }

    private String prepareNotificationWithUpdateInfo() {
      StringBuffer text = new StringBuffer();
      final List<FileGroup> groups = myUpdatedFiles.getTopLevelGroups();
      for (FileGroup group : groups) {
        appendGroup(text, group);
      }
      return text.toString();
    }

    private void appendGroup(final StringBuffer text, final FileGroup group) {
      final int s = group.getFiles().size();
      if (s > 0) {
        text.append("\n");
        text.append(s).append(" ").append(StringUtil.pluralize("File", s)).append(" ").append(group.getUpdateName());
      }

      final List<FileGroup> list = group.getChildren();
      for (FileGroup g : list) {
        appendGroup(text, g);
      }
    }

    public void onSuccess() {
      try {
        onSuccessImpl(false);
      } finally {
        releaseIfNeeded();
      }
    }

    private void onSuccessImpl(final boolean wasCanceled) {
      if ((! myProject.isOpen()) || myProject.isDisposed()) {
        ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
        return;
      }
      boolean continueChain = false;
      for (SequentialUpdatesContext context : myContextInfo.values()) {
        continueChain |= (context != null) && (context.shouldFail());
      }
      final boolean continueChainFinal = continueChain;

      final boolean someSessionWasCancelled = wasCanceled || someSessionWasCanceled(myUpdateSessions);
      // here text conflicts might be interactively resolved
      for (final UpdateSession updateSession : myUpdateSessions) {
        updateSession.onRefreshFilesCompleted();
      }
      // only after conflicts are resolved, put a label
      myAfter = LocalHistory.getInstance().putSystemLabel(myProject, "After update");

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

      WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
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
            } else if (someSessionWasCancelled) {
              ProgressManager.progress(VcsBundle.message("progress.text.updating.canceled"));
            } else {
              ProgressManager.progress(VcsBundle.message("progress.text.updating.done"));
            }

            final boolean noMerged = myUpdatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).isEmpty();
            if (myUpdatedFiles.isEmpty() && myGroupedExceptions.isEmpty()) {
              if (someSessionWasCancelled) {
                VcsBalloonProblemNotifier.showOverChangesView(myProject, VcsBundle.message("progress.text.updating.canceled"), MessageType.WARNING);
              } else {
                VcsBalloonProblemNotifier.showOverChangesView(myProject, getAllFilesAreUpToDateMessage(myRoots), MessageType.INFO);
              }
            }
            else if (! myUpdatedFiles.isEmpty()) {
              showUpdateTree(continueChainFinal && updateSuccess && noMerged, someSessionWasCancelled);

              final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
              cache.processUpdatedFiles(myUpdatedFiles);

              if (someSessionWasCancelled) {
                VcsBalloonProblemNotifier.showOverChangesView(myProject, "VCS Update Incomplete" + prepareNotificationWithUpdateInfo(), MessageType.WARNING);
              } else {
                VcsBalloonProblemNotifier.showOverChangesView(myProject, "VCS Update Finished" + prepareNotificationWithUpdateInfo(), MessageType.INFO);
              }
            }

            ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();

            if (continueChainFinal && updateSuccess) {
              if (!noMerged) {
                showContextInterruptedError();
              } else {
                // trigger next update; for CVS when updating from several branches simultaneously
                reset();
                ProgressManager.getInstance().run(Updater.this);
              }
            }
          }
        }, null, myProject);
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

    private void showUpdateTree(final boolean willBeContinued, final boolean wasCanceled) {
      RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(myProject);
      restoreUpdateTree.registerUpdateInformation(myUpdatedFiles, myActionInfo);
      final String text = getTemplatePresentation().getText() + ((willBeContinued || (myUpdateNumber > 1)) ? ("#" + myUpdateNumber) : "");
      final UpdateInfoTree updateInfoTree = myProjectLevelVcsManager.showUpdateProjectInfo(myUpdatedFiles, text, myActionInfo, wasCanceled);

      updateInfoTree.setBefore(myBefore);
      updateInfoTree.setAfter(myAfter);
      
      updateInfoTree.setCanGroupByChangeList(canGroupByChangelist(myVcsToVirtualFiles.keySet()));
      myProject.getMessageBus().connect(updateInfoTree).subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesAdapter() {
        public void incomingChangesUpdated(final List<CommittedChangeList> receivedChanges) {
          if (receivedChanges != null) {
            updateInfoTree.setChangeLists(receivedChanges);
          }
        }
      });
    }

    public void onCancel() {
      try {
        onSuccessImpl(true);
      } finally {
        releaseIfNeeded();
      }
    }
  }
}
