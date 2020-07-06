// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.vcs.changes.ChangeListUtil.getChangeListNameForUnshelve;
import static com.intellij.openapi.vcs.changes.ChangeListUtil.getPredefinedChangeList;
import static com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.getBinaryShelveChanges;
import static com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.getShelveChanges;

public class UnshelveWithDialogAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = Objects.requireNonNull(getEventProject(e));
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
        VcsBalloonProblemNotifier.showOverChangesView(project, VcsBundle.message("patch.apply.can.t.find.patch.file.warning", changeList.PATH), MessageType.ERROR);
        return;
      }
      List<ShelvedBinaryFilePatch> binaryShelvedPatches =
        ContainerUtil.map(changeList.getBinaryFiles(), ShelvedBinaryFilePatch::new);
      final ApplyPatchDifferentiatedDialog dialog =
        new MyUnshelveDialog(project, virtualFile, changeList, binaryShelvedPatches, e.getData(VcsDataKeys.CHANGES));
      dialog.setHelpId("reference.dialogs.vcs.unshelve"); //NON-NLS
      dialog.show();
    }
  }

  private static void unshelveMultipleShelveChangeLists(@NotNull final Project project,
                                                        @NotNull final List<ShelvedChangeList> changeLists,
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

  private static boolean hasNotAllSelectedChanges(@NotNull ShelvedChangeList list, Change @Nullable [] changes) {
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
                     @NotNull List<ShelvedBinaryFilePatch> binaryShelvedPatches,
                     Change @Nullable [] preselectedChanges) {
      super(project, new UnshelvePatchDefaultExecutor(project, changeList), Collections.emptyList(), ApplyPatchMode.UNSHELVE, patchFile,
            null, getPredefinedChangeList(changeList, ChangeListManager.getInstance(project)), binaryShelvedPatches,
            hasNotAllSelectedChanges(changeList, preselectedChanges) ? Arrays.asList(preselectedChanges) : null,
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
    final JCheckBox removeOptionCheckBox = new JCheckBox(VcsBundle.message("shelve.remove.successfully.applied.files.checkbox"));
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
