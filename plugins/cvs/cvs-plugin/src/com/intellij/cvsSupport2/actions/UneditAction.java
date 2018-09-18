/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author lesya
 */
public class UneditAction extends AbstractActionFromEditGroup {
  @Override
  public void actionPerformed(final CvsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    int modifiedFiles = 0;
    VirtualFile firstModifiedFile = null;
    for(VirtualFile file: selectedFiles) {
      if (FileStatusManager.getInstance(context.getProject()).getStatus(file) == FileStatus.MODIFIED) {
        if (firstModifiedFile == null) {
          firstModifiedFile = file;
        }
        modifiedFiles++;
      }
    }
    if (modifiedFiles > 0) {
      String message;
      if (modifiedFiles == 1) {
        message = CvsBundle.message("unedit.confirmation.single", firstModifiedFile.getPresentableUrl());
      }
      else {
        message = CvsBundle.message("unedit.confirmation.multiple", modifiedFiles);
      }
      if (Messages.showOkCancelDialog(context.getProject(), message, CvsBundle.message("unedit.confirmation.title"), Messages.getQuestionIcon()) != Messages.OK) {
        return;
      }
    }
    super.actionPerformed(context);
  }

  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.unedit");
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    return CommandCvsHandler.createUneditHandler(context.getSelectedFiles(),
                                                 CvsConfiguration.getInstance(context.getProject())
                                                 .MAKE_NEW_FILES_READONLY);
  }
}
