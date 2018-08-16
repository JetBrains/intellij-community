// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
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
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesPreprocess;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class RevertCommittedStuffAbstractAction extends AnAction implements DumbAware {
  private final Convertor<AnActionEvent, Change[]> myForUpdateConvertor;
  private final Convertor<AnActionEvent, Change[]> myForPerformConvertor;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.actions.RevertCommittedStuffAbstractAction");

  public RevertCommittedStuffAbstractAction(final Convertor<AnActionEvent, Change[]> forUpdateConvertor,
                                            final Convertor<AnActionEvent, Change[]> forPerformConvertor) {
    myForUpdateConvertor = forUpdateConvertor;
    myForPerformConvertor = forPerformConvertor;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final Change[] changes = myForPerformConvertor.convert(e);
    if (changes == null || changes.length == 0) return;
    final List<Change> changesList = new ArrayList<>();
    Collections.addAll(changesList, changes);
    FileDocumentManager.getInstance().saveAllDocuments();

    String defaultName = null;
    final ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
    if (changeLists != null && changeLists.length > 0) {
      defaultName = VcsBundle.message("revert.changes.default.name", changeLists[0].getName());
    }

    final ChangeListChooser chooser = new ChangeListChooser(project, ChangeListManager.getInstance(project).getChangeListsCopy(), null,
                                                            "Select Target Changelist", defaultName);
    if (!chooser.showAndGet()) {
      return;
    }

    final List<FilePatch> patches = new ArrayList<>();
    ProgressManager.getInstance().run(new Task.Backgroundable(project, VcsBundle.message("revert.changes.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          final List<Change> preprocessed = ChangesPreprocess.preprocessChangesRemoveDeletedForDuplicateMoved(changesList);
          patches.addAll(IdeaTextPatchBuilder.buildPatch(project, preprocessed, baseDir.getPresentableUrl(), true));
        }
        catch (final VcsException ex) {
          WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> Messages.showErrorDialog(project, "Failed to revert changes: " + ex.getMessage(), VcsBundle.message("revert.changes.title")), null, myProject);
          indicator.cancel();
        }
      }

      @Override
      public void onSuccess() {
        new PatchApplier<BinaryFilePatch>(project, baseDir, patches, chooser.getSelectedList(), null).execute();
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Change[] changes = myForUpdateConvertor.convert(e);

    return project != null && changes != null && changes.length > 0;
  }
}
