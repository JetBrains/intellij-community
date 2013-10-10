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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class MergeFilesAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    DataContext context = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    if (files == null || files.length != 3){
      e.getPresentation().setEnabled(false);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext context = e.getDataContext();
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    if (files == null || files.length != 3){
      return;
    }

    DiffRequestFactory diffRequestFactory = DiffRequestFactory.getInstance();

    VirtualFile file = files[1];
    try {
      String originalText = createValidContent(VfsUtil.loadText(file));
      String leftText = VfsUtil.loadText(files[0]);
      String rightText = VfsUtil.loadText(files[2]);

      Project project = CommonDataKeys.PROJECT.getData(context);
      final MergeRequest diffData = diffRequestFactory.createMergeRequest(leftText, rightText, originalText, file, project,
                                                                          ActionButtonPresentation.APPLY,
                                                                          ActionButtonPresentation.CANCEL_WITH_PROMPT);
      diffData.setVersionTitles(new String[]{files[0].getPresentableUrl(),
                                             files[1].getPresentableUrl(),
                                             files[2].getPresentableUrl()});
      diffData.setWindowTitle(DiffBundle.message("merge.files.dialog.title"));
      diffData.setHelpId("cvs.merge");
      DiffManager.getInstance().getDiffTool().show(diffData);
    }
    catch (IOException e1) {
      Messages.showErrorDialog(DiffBundle.message("merge.dialog.cannot.load.file.error.message", e1.getLocalizedMessage()),
                               DiffBundle.message("merge.files.dialog.title"));
    }
  }
  private static String createValidContent(String str) {
    String[] strings = LineTokenizer.tokenize(str.toCharArray(), false, false);
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < strings.length; i++) {
      String string = strings[i];
      if (i != 0) result.append('\n');
      result.append(string);
    }
    return result.toString();
  }

}
