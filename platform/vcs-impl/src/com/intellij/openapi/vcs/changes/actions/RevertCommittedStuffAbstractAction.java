// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class RevertCommittedStuffAbstractAction extends AnAction implements DumbAware {
  private final boolean myReverse;

  protected RevertCommittedStuffAbstractAction(boolean reverse) {
    myReverse = reverse;
  }

  protected abstract Change @Nullable [] getChanges(@NotNull AnActionEvent e, boolean isFromUpdate);

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final Change[] changes = getChanges(e, false);
    if (changes == null || changes.length == 0) return;
    final List<Change> changesList = new ArrayList<>();
    Collections.addAll(changesList, changes);
    FileDocumentManager.getInstance().saveAllDocuments();

    String defaultName = null;
    final ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);

    if (changeLists != null && changeLists.length > 0) {
      defaultName = VcsBundle.message("changes.revert.apply.change.list.name", myReverse ? 0 : 1, changeLists[0].getName());
    }
    String title = VcsBundle.message("changes.progress.title.choice.revert.apply.changes", myReverse ? 0 : 1);
    String errorPrefix = VcsBundle.message("changes.dialog.message.failed.to.revert.apply.changes", myReverse ? 0 : 1);

    LocalChangeList targetList;
    if (ChangeListManager.getInstance(project).areChangeListsEnabled()) {
      ChangeListChooser chooser = new ChangeListChooser(project, null, null,
                                                        VcsBundle.message("revert.changes.changelist.chooser.title"), defaultName);
      if (!chooser.showAndGet()) return;

      targetList = chooser.getSelectedList();
    }
    else {
      targetList = null;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, title, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<Change> preprocessed = ChangesPreprocess.preprocessChangesRemoveDeletedForDuplicateMoved(changesList);
          List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(project, preprocessed, baseDir.toNioPath(), myReverse, false);
          new PatchApplier(project, baseDir, new ArrayList<>(patches), targetList, null).execute();
        }
        catch (final VcsException ex) {
          WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> {
            Messages.showErrorDialog(project, errorPrefix + ex.getMessage(), title);
          }, null, project);
          indicator.cancel();
        }
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Change[] changes = getChanges(e, true);

    return project != null && changes != null && changes.length > 0;
  }
}
