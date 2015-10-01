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
package com.intellij.openapi.diff.actions;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.ex.DiffContentFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link com.intellij.diff.actions.CompareFilesAction} instead
 */
@Deprecated
public class CompareFiles extends BaseDiffAction {
  public static final DataKey<DiffRequest> DIFF_REQUEST = DataKey.create("CompareFiles.DiffRequest");

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean canShow = isAvailable(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(canShow);      
    }
    else {
      presentation.setVisible(true);
      presentation.setEnabled(canShow);
    }
  }
  
  private static boolean isAvailable(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    DiffRequest diffRequest = e.getData(DIFF_REQUEST);
    if (diffRequest == null) {
      final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      if (virtualFiles == null || virtualFiles.length != 2) {
        return false;
      }
      diffRequest = getDiffRequest(project, virtualFiles);
    }
    if (diffRequest == null) {
      return false;
    }
    return !diffRequest.isSafeToCallFromUpdate() || DiffManager.getInstance().getDiffTool().canShow(diffRequest);
  }

  protected DiffRequest getDiffData(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final DiffRequest diffRequest = DIFF_REQUEST.getData(dataContext);
    if (diffRequest != null) {
      return diffRequest;
    }
    final VirtualFile[] data = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (data == null || data.length != 2) {
      return null;
    }
    return getDiffRequest(project, data);
  }

  @Nullable
  private static DiffRequest getDiffRequest(Project project, VirtualFile[] files) {
    if (files == null || files.length != 2) return null;
    if (files[0].isDirectory() || files[1].isDirectory()
        || files[0].getFileType() instanceof ArchiveFileType
        || files[1].getFileType() instanceof ArchiveFileType) {
      return null;
    }
    String title = DiffBundle.message("diff.element.qualified.name.vs.element.qualified.name.dialog.title",
                                      getVirtualFileContentTitle(files [0]),
                                      getVirtualFileContentTitle(files [1]));
    SimpleDiffRequest diffRequest = DiffContentFactory.compareVirtualFiles(project, files[0], files[1], title);
    if (diffRequest == null) return null;
    diffRequest.setContentTitles(getVirtualFileContentTitle(files [0]),
                                 getVirtualFileContentTitle(files [1]));
    return diffRequest;
  }
}
