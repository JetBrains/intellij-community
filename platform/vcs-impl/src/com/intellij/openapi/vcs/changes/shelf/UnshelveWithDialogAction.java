/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDifferentiatedDialog;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchMode;
import com.intellij.openapi.vcs.changes.patch.UnshelvePatchDefaultExecutor;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.changes.ChangeListUtil.getPredefinedChangeList;
import static com.intellij.util.containers.ContainerUtil.newArrayList;

public class UnshelveWithDialogAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] changeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    if (project == null || changeLists == null || changeLists.length == 0) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    if (changeLists.length > 1) {
      unshelveMultipleShelveChangeLists(e.getData(ShelvedChangesViewManager.SHELVED_CHANGE_KEY), project, changeLists,
                                        e.getData(ShelvedChangesViewManager.SHELVED_BINARY_FILE_KEY));
    }
    else {
      ShelvedChangeList changeList = changeLists[0];
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(changeList.PATH));
      if (virtualFile == null) {
        VcsBalloonProblemNotifier.showOverChangesView(project, "Can not find path file", MessageType.ERROR);
        return;
      }
      List<ShelvedBinaryFilePatch> binaryShelvedPatches =
        ContainerUtil.map(changeList.getBinaryFiles(), ShelvedBinaryFilePatch::new);
      final ApplyPatchDifferentiatedDialog dialog =
        new MyUnshelveDialog(project, virtualFile, changeList, binaryShelvedPatches, e.getData(VcsDataKeys.CHANGES));
      dialog.setHelpId("reference.dialogs.vcs.unshelve");
      dialog.show();
    }
  }

  private static void unshelveMultipleShelveChangeLists(@Nullable List<ShelvedChange> changes,
                                                        @NotNull final Project project,
                                                        @NotNull final ShelvedChangeList[] changeLists,
                                                        @Nullable List<ShelvedBinaryFile> binaryFiles) {
    String suggestedName = changeLists[0].DESCRIPTION;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final ChangeListChooser chooser =
      new ChangeListChooser(project, changeListManager.getChangeListsCopy(), changeListManager.getDefaultChangeList(),
                            VcsBundle.message("unshelve.changelist.chooser.title"), suggestedName) {
        @Nullable
        @Override
        protected JComponent createSouthPanel() {
          return addDoNotShowCheckBox(ObjectUtils.assertNotNull(super.createSouthPanel()), createRemoveFilesStrategyCheckbox(project));
        }
      };

    if (!chooser.showAndGet()) return;

    //todo accept empty collections as a nullable to avoid ugly checks and reassignments
    final List<ShelvedBinaryFile> finalBinaryFiles = binaryFiles == null || binaryFiles.isEmpty() ? null : binaryFiles;
    final List<ShelvedChange> finalChanges = changes == null || changes.isEmpty() ? null : changes;
    final ShelveChangesManager shelveChangesManager = ShelveChangesManager.getInstance(project);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Unshelve Changes", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (ShelvedChangeList changeList : changeLists) {
          shelveChangesManager.unshelveChangeList(changeList, finalChanges, finalBinaryFiles, chooser.getSelectedList(), true);
        }
      }
    });
  }

  private static boolean hasNotAllSelectedChanges(@NotNull Project project, @NotNull ShelvedChangeList list, @Nullable Change[] changes) {
    return changes != null && (list.getChanges(project).size() + list.getBinaryFiles().size()) != changes.length;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    e.getPresentation().setEnabled(project != null && changes != null);
  }

  private static class MyUnshelveDialog extends ApplyPatchDifferentiatedDialog {

    public MyUnshelveDialog(@NotNull Project project,
                            @NotNull VirtualFile patchFile,
                            @NotNull ShelvedChangeList changeList,
                            @NotNull List<ShelvedBinaryFilePatch> binaryShelvedPatches,
                            @Nullable Change[] preselectedChanges) {
      super(project, new UnshelvePatchDefaultExecutor(project, changeList),
            Collections.emptyList(), ApplyPatchMode.UNSHELVE,
            patchFile, null, getPredefinedChangeList(changeList.DESCRIPTION, ChangeListManager.getInstance(project)), binaryShelvedPatches,
            hasNotAllSelectedChanges(project, changeList, preselectedChanges) ? newArrayList(preselectedChanges) : null,
            changeList.DESCRIPTION, true);
    }

    @Nullable
    @Override
    protected JComponent createSouthPanel() {
      return addDoNotShowCheckBox(ObjectUtils.assertNotNull(super.createSouthPanel()), createRemoveFilesStrategyCheckbox(myProject));
    }
  }

  @NotNull
  private static JCheckBox createRemoveFilesStrategyCheckbox(@NotNull Project project) {
    final JCheckBox removeOptionCheckBox = new JCheckBox("Remove successfully applied files from the shelf");
    removeOptionCheckBox.setMnemonic(KeyEvent.VK_R);
    final ShelveChangesManager shelveChangesManager = ShelveChangesManager.getInstance(project);
    removeOptionCheckBox.setSelected(shelveChangesManager.isRemoveFilesFromShelf());
    removeOptionCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shelveChangesManager.setRemoveFilesFromShelf(removeOptionCheckBox.isSelected());
      }
    });
    return removeOptionCheckBox;
  }
}
