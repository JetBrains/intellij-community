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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import java.util.Objects;

import static com.intellij.openapi.vcs.changes.ChangeListUtil.getChangeListNameForUnshelve;
import static com.intellij.openapi.vcs.changes.ChangeListUtil.getPredefinedChangeList;
import static com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.getBinaryShelveChanges;
import static com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.getShelveChanges;
import static com.intellij.util.containers.ContainerUtil.newArrayList;

public class UnshelveWithDialogAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = ObjectUtils.assertNotNull(getEventProject(e));
    DataContext dataContext = e.getDataContext();
    final List<ShelvedChangeList> changeLists = ShelvedChangesViewManager.getShelvedLists(dataContext);
    if (changeLists.isEmpty()) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    if (changeLists.size() > 1) {
      unshelveMultipleShelveChangeLists(project, changeLists, getBinaryShelveChanges(dataContext), getShelveChanges(dataContext));
    }
    else {
      ShelvedChangeList changeList = changeLists.get(0);
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

  private static void unshelveMultipleShelveChangeLists(@NotNull final Project project,
                                                        @NotNull final List<? extends ShelvedChangeList> changeLists,
                                                        @NotNull List<? extends ShelvedBinaryFile> binaryFiles,
                                                        @NotNull List<? extends ShelvedChange> changes) {
    String suggestedName = changeLists.get(0).DESCRIPTION;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final ChangeListChooser chooser =
      new ChangeListChooser(project, changeListManager.getChangeListsCopy(), changeListManager.getDefaultChangeList(),
                            VcsBundle.message("unshelve.changelist.chooser.title"), suggestedName) {
        @Override
        protected JComponent createDoNotAskCheckbox() {
          return createRemoveFilesStrategyCheckbox(project);
        }
      };

    if (!chooser.showAndGet()) return;
    ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project, changeLists, changes, binaryFiles,
                                                                             chooser.getSelectedList());
  }

  private static boolean hasNotAllSelectedChanges(@NotNull ShelvedChangeList list, @Nullable Change[] changes) {
    return changes != null && (Objects.requireNonNull(list.getChanges()).size() + list.getBinaryFiles().size()) != changes.length;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null && !ShelvedChangesViewManager.getShelvedLists(e.getDataContext()).isEmpty());
  }

  private static class MyUnshelveDialog extends ApplyPatchDifferentiatedDialog {

    MyUnshelveDialog(@NotNull Project project,
                     @NotNull VirtualFile patchFile,
                     @NotNull ShelvedChangeList changeList,
                     @NotNull List<? extends ShelvedBinaryFilePatch> binaryShelvedPatches,
                     @Nullable Change[] preselectedChanges) {
      super(project, new UnshelvePatchDefaultExecutor(project, changeList), Collections.emptyList(), ApplyPatchMode.UNSHELVE, patchFile,
            null, getPredefinedChangeList(changeList, ChangeListManager.getInstance(project)), binaryShelvedPatches,
            hasNotAllSelectedChanges(changeList, preselectedChanges) ? newArrayList(preselectedChanges) : null,
            getChangeListNameForUnshelve(changeList), true);
      setOKButtonText(VcsBundle.getString("unshelve.changes.action"));
    }

    @Nullable
    @Override
    protected JComponent createDoNotAskCheckbox() {
      return createRemoveFilesStrategyCheckbox(myProject);
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
