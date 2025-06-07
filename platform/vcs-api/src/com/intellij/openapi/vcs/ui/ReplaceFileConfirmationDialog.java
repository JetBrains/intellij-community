// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.FilePathSplittingPolicy;
import org.jetbrains.annotations.Nls;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;


public class ReplaceFileConfirmationDialog {
  private final FileStatusManager myFileStatusManager;
  ProgressIndicator myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
  private final @Nls String myActionName;

  public ReplaceFileConfirmationDialog(Project project, @Nls String actionName) {
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
                                       createOverwriteButtonName(modifiedFiles), getCancelButtonText(),
                                       Messages.getWarningIcon()) ==
           Messages.OK;

  }

  protected @NlsContexts.Button String getCancelButtonText() {
    return CommonBundle.getCancelButtonText();
  }

  private @NlsContexts.Button String createOverwriteButtonName(Collection modifiedFiles) {
    return modifiedFiles.size() > 1 ? getOkButtonTextForFiles() : getOkButtonTextForOneFile();
  }

  protected @NlsContexts.Button String getOkButtonTextForOneFile() {
    return VcsBundle.message("button.text.overwrite.modified.file");
  }

  protected @NlsContexts.Button String getOkButtonTextForFiles() {
    return VcsBundle.message("button.text.overwrite.modified.files");
  }

  protected @NlsContexts.DialogMessage String createMessage(Collection modifiedFiles) {
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

    ArrayList<VirtualFile> result = new ArrayList<>();

    if (files == null) return result;

    for (VirtualFile file : files) {
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
