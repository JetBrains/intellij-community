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

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

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

    try {
      Project project = CommonDataKeys.PROJECT.getData(context);

      String title = DiffBundle.message("merge.files.dialog.title");
      List<String> titles = ContainerUtil.list(files[0].getPresentableUrl(),
                                               files[1].getPresentableUrl(),
                                               files[2].getPresentableUrl());

      VirtualFile outputFile = files[1];
      List<VirtualFile> contents = ContainerUtil.list(files[0], files[1], files[2]);

      MergeRequest request = diffRequestFactory.createMergeRequestFromFiles(project, outputFile, contents, title, titles, null);
      request.putUserData(DiffUserDataKeys.HELP_ID, "cvs.merge");

      DiffManager.getInstance().showMerge(project, request);
    }
    catch (InvalidDiffRequestException err) {
      Messages.showErrorDialog(err.getLocalizedMessage(), DiffBundle.message("merge.files.dialog.title"));
    }
  }
}
