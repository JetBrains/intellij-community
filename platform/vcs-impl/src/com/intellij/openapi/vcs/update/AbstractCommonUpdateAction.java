/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.AbstractVcsAction;
import com.intellij.openapi.vcs.actions.DescindingFilesFilter;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcs.ViewUpdateInfoNotification;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;
import static com.intellij.util.ObjectUtils.notNull;

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

  @Override
  protected void actionPerformed(@NotNull final VcsContext context) {
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
      catch (ProcessCanceledException ignored) {
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
      if (!dialogOrStatus.showAndGet()) {
        throw new ProcessCanceledException();
      }
    }
  }

  private LinkedHashMap<Configurable, AbstractVcs> createConfigurableToEnvMap(Map<AbstractVcs, Collection<FilePath>> updateEnvToVirtualFiles) {
    LinkedHashMap<Configurable, AbstractVcs> envToConfMap = new LinkedHashMap<>();
    for (AbstractVcs vcs : updateEnvToVirtualFiles.keySet()) {
      Configurable configurable = myActionInfo.getEnvironment(vcs).createConfigurable(updateEnvToVirtualFiles.get(vcs));
      if (configurable != null) {
        envToConfMap.put(configurable, vcs);
      }
    }
    return envToConfMap;
  }

  private Map<AbstractVcs, Collection<FilePath>> createVcsToFilesMap(@NotNull FilePath[] roots, @NotNull Project project) {
    MultiMap<AbstractVcs, FilePath> resultPrep = MultiMap.createSet();
    for (FilePath file : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      if (vcs != null) {
        UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
        if (updateEnvironment != null) {
          resultPrep.putValue(vcs, file);
        }
      }
    }

    final Map<AbstractVcs, Collection<FilePath>> result = new THashMap<>();
    for (Map.Entry<AbstractVcs, Collection<FilePath>> entry : resultPrep.entrySet()) {
      AbstractVcs<?> vcs = entry.getKey();
      result.put(vcs, vcs.filterUniqueRoots(new ArrayList<>(entry.getValue()), FilePath::getVirtualFile));
    }
    return result;
  }

  @NotNull
  private FilePath[] filterRoots(FilePath[] roots, VcsContext vcsContext) {
    final ArrayList<FilePath> result = new ArrayList<>();
    final Project project = vcsContext.getProject();
    assert project != null;
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
              if (VfsUtilCore.isAncestor(virtualFile, vcsRoot, false)) {
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

  @Override
  protected void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
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

  private static boolean supportingVcsesAreEmpty(final ProjectLevelVcsManager vcsManager, final ActionInfo actionInfo) {
    final AbstractVcs[] allActiveVcss = vcsManager.getAllActiveVcss();
    for (AbstractVcs activeVcs : allActiveVcss) {
      if (actionInfo.getEnvironment(activeVcs) != null) return false;
    }
    return true;
  }

  private class Updater extends Task.Backgroundable {
    private final String LOCAL_HISTORY_ACTION = VcsBundle.message("local.history.update.from.vcs");

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
    private LocalHistoryAction myLocalHistoryAction;

    public Updater(final Project project, final FilePath[] roots, final Map<AbstractVcs, Collection<FilePath>> vcsToVirtualFiles) {
      super(project, getTemplatePresentation().getText(), true, VcsConfiguration.getInstance(project).getUpdateOption());
      myProject = project;
      myProjectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project);
      myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
      myRoots = roots;
      myVcsToVirtualFiles = vcsToVirtualFiles;

      myUpdatedFiles = UpdatedFiles.create();
      myGroupedExceptions = new HashMap<>();
      myUpdateSessions = new ArrayList<>();

      // create from outside without any context; context is created by vcses
      myContextInfo = new HashMap<>();
      myUpdateNumber = 1;
    }

    private void reset() {
      myUpdatedFiles = UpdatedFiles.create();
      myGroupedExceptions.clear();
      myUpdateSessions.clear();
      ++ myUpdateNumber;
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      runImpl();
    }

    private void runImpl() {
      ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();
      myProjectLevelVcsManager.startBackgroundVcsOperation();

      myBefore = LocalHistory.getInstance().putSystemLabel(myProject, "Before update");
      myLocalHistoryAction = LocalHistory.getInstance().startAction(LOCAL_HISTORY_ACTION);
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

      try {
        int toBeProcessed = myVcsToVirtualFiles.size();
        int processed = 0;
        for (AbstractVcs vcs : myVcsToVirtualFiles.keySet()) {
          final UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
          updateEnvironment.fillGroups(myUpdatedFiles);
          Collection<FilePath> files = myVcsToVirtualFiles.get(vcs);

          final SequentialUpdatesContext context = myContextInfo.get(vcs);
          final Ref<SequentialUpdatesContext> refContext = new Ref<>(context);

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
      myGroupedExceptions.computeIfAbsent(key, k -> new ArrayList<>()).addAll(list);
    }

    private void doVfsRefresh() {
      LOG.info("Calling refresh files after update for roots: " + Arrays.toString(myRoots));
      RefreshVFsSynchronously.updateAllChanged(myUpdatedFiles);
      notifyAnnotations();
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

    @NotNull
    private Notification prepareNotification(@NotNull UpdateInfoTree tree, boolean someSessionWasCancelled) {
      int allFiles = getUpdatedFilesCount();

      String title;
      String content;
      NotificationType type;
      if (someSessionWasCancelled) {
        title = "Project Partially Updated";
        content = allFiles + " " + pluralize("file", allFiles) + " updated";
        type = NotificationType.WARNING;
      }
      else {
        title = allFiles + " Project " + pluralize("File", allFiles) + " Updated";
        content = notNullize(prepareScopeUpdatedText(tree));
        type = NotificationType.INFORMATION;
      }

      return STANDARD_NOTIFICATION.createNotification(title, content, type, null);
    }

    private int getUpdatedFilesCount() {
      return myUpdatedFiles.getTopLevelGroups().stream().mapToInt(this::getFilesCount).sum();
    }

    private int getFilesCount(@NotNull FileGroup group) {
      return group.getFiles().size() + group.getChildren().stream().mapToInt(g -> getFilesCount(g)).sum();
    }

    @Nullable
    private String prepareScopeUpdatedText(@NotNull UpdateInfoTree tree) {
      String scopeText = null;
      NamedScope scopeFilter = tree.getFilterScope();
      if (scopeFilter != null) {
        int filteredFiles = tree.getFilteredFilesCount();
        String filterName = scopeFilter.getName();
        if (filteredFiles == 0) {
          scopeText = filterName + " wasn't modified";
        }
        else {
          scopeText = filteredFiles + " in " + filterName;
        }
      }
      return scopeText;
    }

    @Override
    public void onSuccess() {
      onSuccessImpl(false);
    }

    private void onSuccessImpl(final boolean wasCanceled) {
      if (!myProject.isOpen() || myProject.isDisposed()) {
        ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
        LocalHistory.getInstance().putSystemLabel(myProject, LOCAL_HISTORY_ACTION); // TODO check why this label is needed
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
      if (myLocalHistoryAction != null) {
        myLocalHistoryAction.finish();
      }
      myAfter = LocalHistory.getInstance().putSystemLabel(myProject, "After update");

      if (myActionInfo.canChangeFileStatus()) {
        final List<VirtualFile> files = new ArrayList<>();
        final RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
        revisionsCache.invalidate(myUpdatedFiles);
        UpdateFilesHelper.iterateFileGroupFiles(myUpdatedFiles, new UpdateFilesHelper.Callback() {
          @Override
          public void onFile(final String filePath, final String groupId) {
            @NonNls final String path = VfsUtilCore.pathToUrl(filePath.replace(File.separatorChar, '/'));
            final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(path);
            if (file != null) {
              files.add(file);
            }
          }
        });
        myDirtyScopeManager.filesDirty(files, null);
      }

      final boolean updateSuccess = !someSessionWasCancelled && myGroupedExceptions.isEmpty();

      WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> {
        if (myProject.isDisposed()) {
          ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
          return;
        }

        if (!myGroupedExceptions.isEmpty()) {
          if (continueChainFinal) {
            gatherContextInterruptedMessages();
          }
          AbstractVcsHelper.getInstance(myProject).showErrors(myGroupedExceptions, VcsBundle.message("message.title.vcs.update.errors",
                                                                                                     getTemplatePresentation().getText()));
        }
        else if (someSessionWasCancelled) {
          ProgressManager.progress(VcsBundle.message("progress.text.updating.canceled"));
        }
        else {
          ProgressManager.progress(VcsBundle.message("progress.text.updating.done"));
        }

        final boolean noMerged = myUpdatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).isEmpty();
        if (myUpdatedFiles.isEmpty() && myGroupedExceptions.isEmpty()) {
          NotificationType type;
          String content;
          if (someSessionWasCancelled) {
            content = VcsBundle.message("progress.text.updating.canceled");
            type = NotificationType.WARNING;
          }
          else {
            content = getAllFilesAreUpToDateMessage(myRoots);
            type = NotificationType.INFORMATION;
          }
          VcsNotifier.getInstance(myProject).notify(STANDARD_NOTIFICATION.createNotification(content, type));
        }
        else if (!myUpdatedFiles.isEmpty()) {
          final UpdateInfoTree tree = showUpdateTree(continueChainFinal && updateSuccess && noMerged, someSessionWasCancelled);
          final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
          cache.processUpdatedFiles(myUpdatedFiles, incomingChangeLists -> tree.setChangeLists(incomingChangeLists));

          Notification notification = prepareNotification(tree, someSessionWasCancelled);
          notification.addAction(new ViewUpdateInfoNotification(myProject, tree, "View"));
          VcsNotifier.getInstance(myProject).notify(notification);
        }

        ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();

        if (continueChainFinal && updateSuccess) {
          if (!noMerged) {
            showContextInterruptedError();
          }
          else {
            // trigger next update; for CVS when updating from several branches simultaneously
            reset();
            ProgressManager.getInstance().run(this);
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

    @NotNull
    private UpdateInfoTree showUpdateTree(final boolean willBeContinued, final boolean wasCanceled) {
      RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(myProject);
      restoreUpdateTree.registerUpdateInformation(myUpdatedFiles, myActionInfo);
      final String text = getTemplatePresentation().getText() + ((willBeContinued || (myUpdateNumber > 1)) ? ("#" + myUpdateNumber) : "");
      UpdateInfoTree updateInfoTree = notNull(myProjectLevelVcsManager.showUpdateProjectInfo(myUpdatedFiles, text, myActionInfo,
                                                                                             wasCanceled));
      updateInfoTree.setBefore(myBefore);
      updateInfoTree.setAfter(myAfter);
      updateInfoTree.setCanGroupByChangeList(canGroupByChangelist(myVcsToVirtualFiles.keySet()));
      return updateInfoTree;
    }

    @Override
    public void onCancel() {
      onSuccessImpl(true);
    }
  }
}
