// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.changes.ui.RollbackProgressModifier;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcs.commit.CommitMode;
import com.intellij.vcs.commit.CommitModeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_POPUP;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.ui.Messages.showYesNoDialog;
import static com.intellij.openapi.util.text.StringUtil.ELLIPSIS;
import static com.intellij.openapi.vcs.changes.actions.RollbackFilesAction.isPreferCheckboxesOverSelection;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.util.ui.UIUtil.removeMnemonic;
import static com.intellij.vcsUtil.RollbackUtil.getRollbackOperationName;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class RollbackAction extends AnAction implements DumbAware, UpdateInBackground {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);

    Project project = e.getProject();
    if (project == null || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) return;
    if (isPreferCheckboxesOverSelection() &&
        CommitModeManager.getInstance(project).getCurrentCommitMode() instanceof CommitMode.NonModalCommitMode &&
        CHANGES_VIEW_POPUP.equals(e.getPlace())) {
      return;
    }

    Change[] leadSelection = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION);
    boolean hasDataToRollback =
      !ArrayUtil.isEmpty(leadSelection) ||
      Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_LOCALLY_DELETED)) ||
      Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING)) ||
      Boolean.TRUE.equals(e.getData(VcsDataKeys.HAVE_SELECTED_CHANGES)) ||
      !getIncludedChanges(e).isEmpty() ||
      hasReversibleFiles(e) ||
      currentChangelistNotEmpty(project);

    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(hasDataToRollback);
    e.getPresentation().setText(getRollbackOperationName(project) + ELLIPSIS);
  }

  private static boolean hasReversibleFiles(@NotNull AnActionEvent e) {
    ChangeListManager manager = ChangeListManager.getInstance(e.getRequiredData(CommonDataKeys.PROJECT));
    Set<VirtualFile> modifiedWithoutEditing = new HashSet<>(manager.getModifiedWithoutEditing());

    return JBIterable.from(e.getData(VcsDataKeys.VIRTUAL_FILES)).filter(
      file -> manager.haveChangesUnder(file) != ThreeState.NO || manager.isFileAffected(file) || modifiedWithoutEditing.contains(file))
      .isNotEmpty();
  }

  private static boolean currentChangelistNotEmpty(Project project) {
    ChangeListManager clManager = ChangeListManager.getInstance(project);
    ChangeList list = clManager.getDefaultChangeList();
    return !list.getChanges().isEmpty();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!RollbackFilesAction.checkClmActive(e)) return;

    Project project = requireNonNull(e.getProject());
    List<FilePath> missingFiles = e.getData(ChangesListView.MISSING_FILES_DATA_KEY);
    Collection<Change> changes = getChanges(e);
    LinkedHashSet<VirtualFile> modifiedWithoutEditing = getModifiedWithoutEditing(e, project);
    if (modifiedWithoutEditing != null) {
      changes = filter(changes, change -> !modifiedWithoutEditing.contains(change.getVirtualFile()));
    }


    FileDocumentManager.getInstance().saveAllDocuments();

    boolean hasChanges = false;
    if (missingFiles != null && !missingFiles.isEmpty()) {
      hasChanges = true;
      new RollbackDeletionAction().actionPerformed(e);
    }

    if (modifiedWithoutEditing != null && !modifiedWithoutEditing.isEmpty()) {
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

  private static @NotNull Collection<Change> getChanges(@NotNull AnActionEvent e) {
    Collection<Change> includedChanges = getIncludedChanges(e);
    if (!includedChanges.isEmpty()) return includedChanges;

    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes == null) {
      final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      if (files != null) {
        final ChangeListManager clManager = ChangeListManager.getInstance(requireNonNull(e.getProject()));
        final List<Change> changesList = new ArrayList<>();
        for (VirtualFile vf : files) {
          changesList.addAll(clManager.getChangesIn(vf));
        }
        if (!changesList.isEmpty()) {
          changes = changesList.toArray(new Change[0]);
        }
      }
    }

    if (!ArrayUtil.isEmpty(changes)) return newArrayList(changes);
    return emptyList();
  }

  private static @NotNull Collection<Change> getIncludedChanges(@NotNull AnActionEvent e) {
    if (!isPreferCheckboxesOverSelection()) return emptyList();

    ChangesListView changesView = e.getData(ChangesListView.DATA_KEY);
    if (changesView == null) return emptyList();

    return filterIsInstance(changesView.getInclusionModel().getInclusion(), Change.class);
  }

  @Nullable
  private static LinkedHashSet<VirtualFile> getModifiedWithoutEditing(final AnActionEvent e, Project project) {
    final List<VirtualFile> modifiedWithoutEditing = e.getData(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY);
    if (modifiedWithoutEditing != null && modifiedWithoutEditing.size() > 0) {
      return new LinkedHashSet<>(modifiedWithoutEditing);
    }

    final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length > 0) {
      LinkedHashSet<VirtualFile> result = new LinkedHashSet<>(Arrays.asList(virtualFiles));
      result.retainAll(ChangeListManager.getInstance(project).getModifiedWithoutEditing());
      return result;
    }

    return null;
  }

  private static void rollbackModifiedWithoutEditing(final Project project, final LinkedHashSet<VirtualFile> modifiedWithoutEditing) {
    final String operationName = StringUtil.decapitalize(removeMnemonic(getRollbackOperationName(project)));
    String message = (modifiedWithoutEditing.size() == 1)
                     ? VcsBundle.message("rollback.modified.without.editing.confirm.single",
                                         operationName, modifiedWithoutEditing.iterator().next().getPresentableUrl())
                     : VcsBundle.message("rollback.modified.without.editing.confirm.multiple",
                                         operationName, modifiedWithoutEditing.size());
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
