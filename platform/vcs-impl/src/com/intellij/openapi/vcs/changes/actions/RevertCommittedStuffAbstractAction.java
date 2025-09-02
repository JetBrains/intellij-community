// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.history.ActivityId;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.vcs.VcsActivity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public abstract class RevertCommittedStuffAbstractAction extends AnAction implements DumbAware {
  private final boolean myReverse;

  protected RevertCommittedStuffAbstractAction(boolean reverse) {
    myReverse = reverse;
  }

  protected abstract Change @Nullable [] getChanges(@NotNull AnActionEvent e, boolean isFromUpdate);

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    VirtualFile baseDir = Objects.requireNonNull(project.getBaseDir());
    final Change[] changes = getChanges(e, false);
    if (changes == null || changes.length == 0) return;
    final List<Change> changesList = new ArrayList<>();
    Collections.addAll(changesList, changes);
    FileDocumentManager.getInstance().saveAllDocuments();

    final ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);

    String title = VcsBundle.message("changes.progress.title.choice.revert.apply.changes", myReverse ? 0 : 1);
    String errorPrefix = VcsBundle.message("changes.dialog.message.failed.to.revert.apply.changes", myReverse ? 0 : 1);

    LocalChangeList targetList;
    if (ChangeListManager.getInstance(project).areChangeListsEnabled()) {
      ChangeListChooser chooser = new MyChangeListChooser(project, VcsBundle.message("revert.changes.changelist.chooser.title"));
      if (changeLists != null && changeLists.length > 0) {
        String defaultName = VcsBundle.message("changes.revert.apply.change.list.name", myReverse ? 0 : 1, changeLists[0].getName());
        chooser.setSuggestedName(defaultName);
      }
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
          String activityName = myReverse ? VcsBundle.message("activity.name.rollback") : VcsBundle.message("activity.name.apply.patch");
          ActivityId activityId = myReverse ? VcsActivity.Rollback : VcsActivity.ApplyPatch;
          new PatchApplier(project, baseDir, new ArrayList<>(patches), targetList, null, false, null, null,
                           activityName, activityId).execute();
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

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Change[] changes = getChanges(e, true);

    return project != null && changes != null && changes.length > 0;
  }

  private static class MyChangeListChooser extends ChangeListChooser {
    MyChangeListChooser(@NotNull Project project, @NlsContexts.DialogTitle String title) {
      super(project, title);
    }

    @Override
    final protected @NotNull String getHelpId() {
      return "reference.dialogs.vcs.undo.commit";
    }
  }
}
