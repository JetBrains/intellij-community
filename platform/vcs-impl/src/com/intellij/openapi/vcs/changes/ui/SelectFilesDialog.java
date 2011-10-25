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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class SelectFilesDialog extends AbstractSelectFilesDialog<VirtualFile> {

  private ChangesTreeList<VirtualFile> myFileList;

  public SelectFilesDialog(final Project project, List<VirtualFile> originalFiles, final String prompt,
                           final VcsShowConfirmationOption confirmationOption, boolean selectableFiles, boolean showDoNotAskOption) {
    super(project, false, confirmationOption, prompt, showDoNotAskOption);
    myFileList = new ChangesTreeList<VirtualFile>(project, originalFiles, selectableFiles, true, null, null) {
      protected DefaultTreeModel buildTreeModel(final List<VirtualFile> changes, ChangeNodeDecorator changeNodeDecorator) {
        return new TreeModelBuilder(project, false).buildModelFromFiles(changes);
      }

      protected List<VirtualFile> getSelectedObjects(final ChangesBrowserNode node) {
        return node.getAllFilesUnder();
      }

      protected VirtualFile getLeadSelectedObject(final ChangesBrowserNode node) {
        final Object o = node.getUserObject();
        if (o instanceof VirtualFile) {
          return (VirtualFile) o;
        }
        return null;
      }
    };
    myFileList.setChangesToDisplay(originalFiles);
    init();
  }

  public Collection<VirtualFile> getSelectedFiles() {
    return myFileList.getIncludedChanges();
  }

  @NotNull
  @Override
  protected ChangesTreeList getFileList() {
    return myFileList;
  }
}
