/*
 * Copyright 2012 Vladimir Rudev
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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.mergeTool.MergeTool;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * vladimir.ru
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class MergeApplication extends DiffApplication {
  public String getCommandName() {
    return "merge";
  }

  public void premain(String[] args) {
    if (args.length != 5) {
      printHelp(DiffBundle.message("merge.application.usage.parameters.and.description"));
    }
  }

  @Override
  public void processExternalCommandLine(String[] args) {
    if (args.length != 5) {
      String productName = ApplicationNamesInfo.getInstance().getProductName();
      Messages.showMessageDialog("Usage: " + productName.toLowerCase() + " merge <leftfile> <rightfile> <original> <result>", "Merge",
                                 Messages.getInformationIcon());
      return;
    }
    try {
      processDiffCommand(args);
    }
    catch (Exception e) {
      Messages.showMessageDialog("Error showing merge: " + e.getMessage(), "Merge", Messages.getErrorIcon());
    }
  }

  protected void processDiffCommand(String[] args) throws IOException {
    final String path1 = args[1];
    final String path2 = args[2];
    final String path3 = args[3];
    final String path4 = args[4];
    final VirtualFile left = findFile(path1);
    final VirtualFile right = findFile(path2);
    final VirtualFile middle = findFile(path3);
    final VirtualFile result = findOrCreateFile(path4);
    VirtualFile[] files = new VirtualFile[]{left, right, middle, result};
    if (haveDirs(files)) {
      throw new FileNotFoundException(DiffBundle.message("merge.dirs.error.message"));
    }

    String leftText = getText(left);
    String rightText = getText(right);
    String middleText = getText(middle);
    MergeRequest request = DiffRequestFactory.getInstance()
      .createMergeRequest(leftText, rightText, middleText, result, null, ActionButtonPresentation.APPLY,
                          ActionButtonPresentation.CANCEL_WITH_PROMPT);
    request.addHint(DiffTool.HINT_SHOW_MODAL_DIALOG);
    request.setWindowTitle("Merge");
    request.setVersionTitles(new String[]{left.getPresentableUrl(), result.getPresentableUrl(), middle.getPresentableUrl()});
    new MergeTool().show(request);
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private static VirtualFile findOrCreateFile(String path) throws IOException {
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
    if (file == null) {
      boolean result = new File(path).createNewFile();
      if (result) {
        return findFile(path);
      }
      else {
        throw new FileNotFoundException(DiffBundle.message("cannot.create.file.error.message", path));
      }
    }
    return file;
  }

  private static String getText(VirtualFile file) throws IOException {
    InputStream inputStream = file.getInputStream();
    try {
      return StreamUtil.readText(inputStream);
    }
    finally {
      inputStream.close();
    }
  }

  private static boolean haveDirs(VirtualFile... files) {
    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        return true;
      }
    }
    return false;
  }
}
