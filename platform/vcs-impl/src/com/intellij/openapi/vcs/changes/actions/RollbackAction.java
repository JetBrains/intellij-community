// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.changes.ui.RollbackProgressModifier;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.commit.CommitMode;
import com.intellij.vcs.commit.CommitModeManager;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_POPUP;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.ui.Messages.showYesNoDialog;
import static com.intellij.openapi.util.text.StringUtil.ELLIPSIS;
import static com.intellij.openapi.vcs.changes.actions.RollbackFilesAction.Manager.isPreferCheckboxesOverSelection;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.filterIsInstance;
import static com.intellij.util.ui.UIUtil.removeMnemonic;
import static com.intellij.vcsUtil.RollbackUtil.getRollbackOperationName;
import static java.util.Collections.emptyList;

public final class RollbackAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) return;
    if (isPreferCheckboxesOverSelection() &&
        CommitModeManager.getInstance(project).getCurrentCommitMode() instanceof CommitMode.NonModalCommitMode &&
        CHANGES_VIEW_POPUP.equals(e.getPlace())) {
      return;
    }

    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(hasReversibleFiles(e));
    e.getPresentation().setText(getRollbackOperationName(project) + ELLIPSIS);
  }

  private static boolean hasReversibleFiles(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ChangeListManager clmManager = ChangeListManager.getInstance(project);

    if (!clmManager.getAllChanges().isEmpty()) {
      return true;
    }

    List<FilePath> missingFiles = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    if (!ContainerUtil.isEmpty(missingFiles)) {
      return true;
    }

    Set<VirtualFile> modifiedWithoutEditing = getModifiedWithoutEditing(e, project);
    if (!ContainerUtil.isEmpty(modifiedWithoutEditing)) {
      return true;
    }

    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!RollbackFilesAction.Manager.checkClmActive(e)) return;

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    List<FilePath> missingFiles = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    Collection<? extends Change> changes = getSelectedChanges(e);
    Set<VirtualFile> modifiedWithoutEditing = getModifiedWithoutEditing(e, project);
    if (modifiedWithoutEditing != null) {
      changes = filter(changes, change -> !modifiedWithoutEditing.contains(change.getVirtualFile()));
    }


    FileDocumentManager.getInstance().saveAllDocuments();

    boolean hasChanges = false;
    if (!ContainerUtil.isEmpty(missingFiles)) {
      hasChanges = true;
      new RollbackDeletionAction().actionPerformed(e);
    }

    if (!ContainerUtil.isEmpty(modifiedWithoutEditing)) {
      hasChanges = true;
      rollbackModifiedWithoutEditing(project, modifiedWithoutEditing);
    }

    if (!changes.isEmpty()) {
      RollbackChangesDialog.rollbackChanges(project, changes);
    }
    else if (!hasChanges) {
      RollbackChangesDialog.rollbackChanges(project);
    }
  }

  private static @NotNull Collection<? extends Change> getSelectedChanges(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);

    ChangesListView changesView = e.getData(ChangesListView.DATA_KEY);
    if (isPreferCheckboxesOverSelection() && changesView != null) {
      return filterIsInstance(changesView.getInclusionModel().getInclusion(), Change.class);
    }

    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes != null) {
      return Arrays.asList(changes);
    }

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (!ArrayUtil.isEmpty(files)) {
      Collection<Change> allChanges = ChangeListManager.getInstance(project).getAllChanges();
      return VcsImplUtil.filterChangesUnderFiles(allChanges, List.of(files)).toList();
    }

    return emptyList();
  }

  @Nullable
  private static Set<VirtualFile> getModifiedWithoutEditing(final AnActionEvent e, Project project) {
    final List<VirtualFile> selectedModifiedWithoutEditing = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    if (!ContainerUtil.isEmpty(selectedModifiedWithoutEditing)) {
      return new HashSet<>(selectedModifiedWithoutEditing);
    }

    final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (!ArrayUtil.isEmpty(virtualFiles)) {
      Set<VirtualFile> modifiedWithoutEditing = new HashSet<>(ChangeListManager.getInstance(project).getModifiedWithoutEditing());
      Set<VirtualFile> files = ContainerUtil.newHashSet(virtualFiles);
      modifiedWithoutEditing.retainAll(files);
      return modifiedWithoutEditing;
    }

    return null;
  }

  private static void rollbackModifiedWithoutEditing(final Project project, final Set<? extends VirtualFile> modifiedWithoutEditing) {
    String operationName = removeMnemonic(getRollbackOperationName(project));
    String decapitalizedOperationName = StringUtil.decapitalize(operationName);
    String message = (modifiedWithoutEditing.size() == 1)
                     ? VcsBundle.message("rollback.modified.without.editing.confirm.single",
                                         decapitalizedOperationName, modifiedWithoutEditing.iterator().next().getPresentableUrl())
                     : VcsBundle.message("rollback.modified.without.editing.confirm.multiple",
                                         decapitalizedOperationName, modifiedWithoutEditing.size());
    int rc = showYesNoDialog(project, message, VcsBundle.message("changes.action.rollback.title", operationName), getQuestionIcon());
    if (rc != Messages.YES) {
      return;
    }
    final List<VcsException> exceptions = new ArrayList<>();

    final ProgressManager progressManager = ProgressManager.getInstance();
    final Runnable action = () -> {
      final ProgressIndicator indicator = progressManager.getProgressIndicator();
      try {
        ChangesUtil.processVirtualFilesByVcs(project, modifiedWithoutEditing, (vcs, items) -> {
          final RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
          if (rollbackEnvironment != null) {
            if (indicator != null) {
              indicator.setText(VcsBundle.message("progress.text.performing", vcs.getDisplayName(),
                                                  StringUtil.toLowerCase(removeMnemonic(rollbackEnvironment.getRollbackOperationName()))));
              indicator.setIndeterminate(false);
            }
            rollbackEnvironment
              .rollbackModifiedWithoutCheckout(items, exceptions, new RollbackProgressModifier(items.size(), indicator));
            if (indicator != null) {
              indicator.setText2("");
            }
          }
        });
      }
      catch (ProcessCanceledException e) {
        // for files refresh
      }
      if (!exceptions.isEmpty()) {
        AbstractVcsHelper.getInstance(project).showErrors(exceptions, VcsBundle.message("rollback.modified.without.checkout.error.tab",
                                                                                        operationName));
      }

      VfsUtil.markDirty(true, false, VfsUtilCore.toVirtualFileArray(modifiedWithoutEditing));

      VirtualFileManager.getInstance().asyncRefresh(() -> {
        for (VirtualFile virtualFile : modifiedWithoutEditing) {
          VcsDirtyScopeManager.getInstance(project).fileDirty(virtualFile);
        }
      });
    };
    progressManager.runProcessWithProgressSynchronously(action, operationName, true, project);
  }
}
