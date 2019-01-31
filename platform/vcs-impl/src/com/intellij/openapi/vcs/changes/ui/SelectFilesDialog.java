// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.actionSystem.EmptyAction.setupAction;

/**
 * @author yole
 */
public class SelectFilesDialog extends AbstractSelectFilesDialog {

  @NotNull private final VirtualFileList myFileList;
  private final boolean myDeletableFiles;

  @Deprecated
  protected SelectFilesDialog(Project project,
                              @NotNull List<? extends VirtualFile> files,
                              @Nullable String prompt,
                              @Nullable VcsShowConfirmationOption confirmationOption,
                              boolean selectableFiles,
                              boolean showDoNotAskOption,
                              boolean deletableFiles) {
    this(project, files, prompt, showDoNotAskOption ? confirmationOption : null, selectableFiles, deletableFiles);
  }

  protected SelectFilesDialog(Project project,
                              @NotNull List<? extends VirtualFile> files,
                              @Nullable String prompt,
                              @Nullable VcsShowConfirmationOption confirmationOption,
                              boolean selectableFiles,
                              boolean deletableFiles) {
    super(project, false, confirmationOption, prompt);
    myDeletableFiles = deletableFiles;
    myFileList = new VirtualFileList(project, selectableFiles, deletableFiles, files);
  }

  @NotNull
  @Deprecated
  public static SelectFilesDialog init(Project project,
                                       @NotNull List<? extends VirtualFile> originalFiles,
                                       @Nullable String prompt,
                                       @Nullable VcsShowConfirmationOption confirmationOption,
                                       boolean selectableFiles,
                                       boolean showDoNotAskOption,
                                       boolean deletableFiles) {
    return init(project, originalFiles, prompt, showDoNotAskOption ? confirmationOption : null, selectableFiles, deletableFiles);
  }

  @NotNull
  public static SelectFilesDialog init(Project project,
                                       @NotNull List<? extends VirtualFile> originalFiles,
                                       @Nullable String prompt,
                                       @Nullable VcsShowConfirmationOption confirmationOption,
                                       boolean selectableFiles,
                                       boolean deletableFiles) {
    SelectFilesDialog dialog = new SelectFilesDialog(project, originalFiles, prompt, confirmationOption, selectableFiles, deletableFiles);
    dialog.init();
    return dialog;
  }

  @NotNull
  public static SelectFilesDialog init(Project project,
                                       @NotNull List<? extends VirtualFile> originalFiles,
                                       @Nullable String prompt,
                                       @Nullable VcsShowConfirmationOption confirmationOption,
                                       boolean selectableFiles,
                                       boolean deletableFiles,
                                       @NotNull String okActionName,
                                       @NotNull String cancelActionName) {
    final SelectFilesDialog dlg = init(project, originalFiles, prompt, confirmationOption, selectableFiles, deletableFiles);
    dlg.setOKButtonText(okActionName);
    dlg.setCancelButtonText(cancelActionName);
    return dlg;
  }

  public Collection<VirtualFile> getSelectedFiles() {
    return myFileList.getIncludedChanges();
  }

  public void setSelectedFiles(@NotNull final Collection<VirtualFile> selected) {
    myFileList.setIncludedChanges(selected);
    myFileList.rebuildTree();
  }

  @NotNull
  @Override
  protected ChangesTree getFileList() {
    return myFileList;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createToolbarActions() {
    DefaultActionGroup defaultGroup = super.createToolbarActions();
    if (myDeletableFiles) {
      AnAction deleteAction = new DeleteAction(null, null, IconUtil.getRemoveIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          super.actionPerformed(e);
          myFileList.refresh();
        }
      };
      setupAction(deleteAction, IdeActions.ACTION_DELETE, getFileList());
      defaultGroup.add(deleteAction);
    }
    return defaultGroup;
  }

  public static class VirtualFileList extends ChangesTreeImpl.VirtualFiles {
    @Nullable private final DeleteProvider myDeleteProvider;

    public VirtualFileList(Project project, boolean selectableFiles, boolean deletableFiles, @NotNull List<? extends VirtualFile> files) {
      super(project, selectableFiles, true, files);
      myDeleteProvider = (deletableFiles ?  new VirtualFileDeleteProvider() : null);
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) && myDeleteProvider != null) {
        return myDeleteProvider;
      }
      else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return ArrayUtil.toObjectArray(getSelectedChanges(), VirtualFile.class);
      }

      return super.getData(dataId);
    }

    public void refresh() {
      setChangesToDisplay(ContainerUtil.filter(getChanges(), VirtualFile::isValid));
    }
  }
}
