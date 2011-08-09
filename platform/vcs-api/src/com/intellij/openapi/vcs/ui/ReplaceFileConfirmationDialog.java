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
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.FilePathSplittingPolicy;
import com.intellij.CommonBundle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;


public class ReplaceFileConfirmationDialog {
  private final FileStatusManager myFileStatusManager;
  ProgressIndicator myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
  private final String myActionName;

  public ReplaceFileConfirmationDialog(Project project, String actionName) {
    myFileStatusManager = FileStatusManager.getInstance(project);
    myActionName = actionName;
  }

  public boolean confirmFor(VirtualFile[] files) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;
    if (myProgressIndicator != null) myProgressIndicator.pushState();
    try {
      Collection modifiedFiles = collectModifiedFiles(files);
      if (modifiedFiles.isEmpty()) return true;
      return requestConfirmation(modifiedFiles);
    }
    finally {
      if (myProgressIndicator != null) myProgressIndicator.popState();
    }
  }

  public boolean requestConfirmation(final Collection modifiedFiles) {
    if (modifiedFiles.isEmpty()) return true;

    return Messages.showOkCancelDialog(createMessage(modifiedFiles), myActionName,
                                    createOwerriteButtonName(modifiedFiles), getCancelButtonText(),
                               Messages.getWarningIcon()) ==
                DialogWrapper.OK_EXIT_CODE;

  }

  protected String getCancelButtonText() {
    return CommonBundle.getCancelButtonText();
  }

  private String createOwerriteButtonName(Collection modifiedFiles) {

    return modifiedFiles.size() > 1 ? getOkButtonTextForFiles() : getOkButtonTextForOneFile();
  }

  protected String getOkButtonTextForOneFile() {
    return VcsBundle.message("button.text.overwrite.modified.file");
  }

  protected String getOkButtonTextForFiles() {
    return VcsBundle.message("button.text.overwrite.modified.files");
  }

  protected String createMessage(Collection modifiedFiles) {
    if (modifiedFiles.size() == 1) {
      VirtualFile virtualFile = ((VirtualFile)modifiedFiles.iterator().next());
      return VcsBundle.message("message.text.file.locally.modified",
                               FilePathSplittingPolicy.SPLIT_BY_LETTER.getPresentableName(new File(virtualFile.getPath()), 40));
    }
    else {
      return VcsBundle.message("message.text.several.files.locally.modified");
    }
  }

  public Collection<VirtualFile> collectModifiedFiles(VirtualFile[] files) {

    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    if (files == null) return result;

    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      if (myProgressIndicator != null) {
        myProgressIndicator.setText(VcsBundle.message("progress.text.searching.for.modified.files"));
        myProgressIndicator.setText2(file.getPresentableUrl());
      }
      FileStatus status = myFileStatusManager.getStatus(file);
      if (status != FileStatus.NOT_CHANGED) {
        result.add(file);
        if (result.size() > 1) return result;
      }
      result.addAll(collectModifiedFiles(file.getChildren()));
    }
    return result;
  }
}
