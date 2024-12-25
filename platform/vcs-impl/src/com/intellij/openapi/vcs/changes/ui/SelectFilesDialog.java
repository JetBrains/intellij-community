// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;
import java.util.List;


public class SelectFilesDialog extends AbstractSelectFilesDialog {

  private final @NotNull VirtualFileList myFileList;
  private final boolean myDeletableFiles;

  protected SelectFilesDialog(Project project,
                              @NotNull List<? extends VirtualFile> files,
                              @Nullable @NlsContexts.Label String prompt,
                              @Nullable VcsShowConfirmationOption confirmationOption,
                              boolean selectableFiles,
                              boolean deletableFiles) {
    super(project, false, confirmationOption, prompt);
    myDeletableFiles = deletableFiles;
    myFileList = new VirtualFileList(project, selectableFiles, deletableFiles, files);
  }

  @Deprecated(forRemoval = true)
  public static @NotNull SelectFilesDialog init(Project project,
                                                @NotNull List<? extends VirtualFile> originalFiles,
                                                @Nullable @NlsContexts.Label String prompt,
                                                @Nullable VcsShowConfirmationOption confirmationOption,
                                                boolean selectableFiles,
                                                boolean showDoNotAskOption,
                                                boolean deletableFiles) {
    return init(project, originalFiles, prompt, showDoNotAskOption ? confirmationOption : null, selectableFiles, deletableFiles);
  }

  public static @NotNull SelectFilesDialog init(Project project,
                                                @NotNull List<? extends VirtualFile> originalFiles,
                                                @Nullable @NlsContexts.Label String prompt,
                                                @Nullable VcsShowConfirmationOption confirmationOption,
                                                boolean selectableFiles,
                                                boolean deletableFiles) {
    SelectFilesDialog dialog = new SelectFilesDialog(project, originalFiles, prompt, confirmationOption, selectableFiles, deletableFiles);
    dialog.init();
    return dialog;
  }

  public static @NotNull SelectFilesDialog init(Project project,
                                                @NotNull List<? extends VirtualFile> originalFiles,
                                                @Nullable @NlsContexts.Label String prompt,
                                                @Nullable VcsShowConfirmationOption confirmationOption,
                                                boolean selectableFiles,
                                                boolean deletableFiles,
                                                @NotNull @NlsContexts.Button String okActionName,
                                                @NotNull @NlsContexts.Button String cancelActionName) {
    final SelectFilesDialog dlg = init(project, originalFiles, prompt, confirmationOption, selectableFiles, deletableFiles);
    dlg.setOKButtonText(okActionName);
    dlg.setCancelButtonText(cancelActionName);
    return dlg;
  }

  public Collection<VirtualFile> getSelectedFiles() {
    return myFileList.getIncludedChanges();
  }

  public void setSelectedFiles(final @NotNull Collection<VirtualFile> selected) {
    myFileList.setIncludedChanges(selected);
    myFileList.rebuildTree();
  }

  @Override
  protected @NotNull ChangesTree getFileList() {
    return myFileList;
  }

  @Override
  protected @NotNull DefaultActionGroup createToolbarActions() {
    DefaultActionGroup defaultGroup = super.createToolbarActions();
    if (myDeletableFiles) {
      AnAction deleteAction = new DeleteAction(null, null, IconUtil.getRemoveIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          super.actionPerformed(e);
          myFileList.rebuildTree();
        }
      };
      ActionUtil.mergeFrom(deleteAction, IdeActions.ACTION_DELETE);
      deleteAction.registerCustomShortcutSet(getFileList(), null);
      defaultGroup.add(deleteAction);
    }
    return defaultGroup;
  }

  public static class VirtualFileList extends AsyncChangesTreeImpl.VirtualFiles {
    private final @Nullable DeleteProvider myDeleteProvider;

    public VirtualFileList(Project project, boolean selectableFiles, boolean deletableFiles, @NotNull List<? extends VirtualFile> files) {
      super(project, selectableFiles, true, files);
      myDeleteProvider = (deletableFiles ? new VirtualFileDeleteProvider() : null);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      super.uiDataSnapshot(sink);
      sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);
      sink.set(CommonDataKeys.VIRTUAL_FILE_ARRAY,
               getSelectedChanges().toArray(VirtualFile.EMPTY_ARRAY));
    }

    @Override
    protected @NotNull DefaultTreeModel buildTreeModel(@NotNull ChangesGroupingPolicyFactory grouping,
                                                       @NotNull List<? extends VirtualFile> changes) {
      return super.buildTreeModel(grouping, ContainerUtil.filter(changes, VirtualFile::isValid));
    }
  }
}
