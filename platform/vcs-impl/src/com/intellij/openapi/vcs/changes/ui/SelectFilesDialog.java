/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.actions.DeleteUnversionedFilesAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class SelectFilesDialog extends AbstractSelectFilesDialog<VirtualFile> {

  @NotNull private final VirtualFileList myFileList;
  private final boolean myDeletableFiles;

  protected SelectFilesDialog(Project project,
                              @NotNull List<VirtualFile> files,
                              @Nullable String prompt,
                              @Nullable VcsShowConfirmationOption confirmationOption,
                              boolean selectableFiles,
                              boolean showDoNotAskOption,
                              boolean deletableFiles) {
    super(project, false, confirmationOption, prompt, showDoNotAskOption);
    myDeletableFiles = deletableFiles;
    myFileList = new VirtualFileList(project, selectableFiles, deletableFiles, files);
  }

  @NotNull
  public static SelectFilesDialog init(Project project,
                                       @NotNull List<VirtualFile> originalFiles,
                                       @Nullable String prompt,
                                       @Nullable VcsShowConfirmationOption confirmationOption,
                                       boolean selectableFiles,
                                       boolean showDoNotAskOption,
                                       boolean deletableFiles) {
    SelectFilesDialog dialog = new SelectFilesDialog(project, originalFiles, prompt, confirmationOption, selectableFiles,
                                                     showDoNotAskOption, deletableFiles);
    dialog.init();
    return dialog;
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
      AnAction deleteAction = new DeleteUnversionedFilesAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          super.actionPerformed(e);
          myFileList.refresh();
        }
      };
      defaultGroup.add(deleteAction);
      deleteAction.registerCustomShortcutSet(CommonShortcuts.getDelete(), this.getFileList());
    }
    return defaultGroup;
  }

  public static class VirtualFileList extends ChangesTreeImpl.VirtualFiles {
    @Nullable private final DeleteProvider myDeleteProvider;

    public VirtualFileList(Project project, boolean selectableFiles, boolean deletableFiles, @NotNull List<VirtualFile> files) {
      super(project, selectableFiles, true, files);
      myDeleteProvider = (deletableFiles ?  new VirtualFileDeleteProvider() : null);
    }

    @Nullable
    @Override
    public Object getData(String dataId) {
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
