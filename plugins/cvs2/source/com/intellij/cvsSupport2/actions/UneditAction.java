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
      if (Messages.showOkCancelDialog(context.getProject(), message, CvsBundle.message("unedit.confirmation.title"), Messages.getQuestionIcon()) != 0) {
        return;
      }
    }
    super.actionPerformed(context);
  }

  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.unedit");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    return CommandCvsHandler.createUneditHandler(context.getSelectedFiles(),
                                                 CvsConfiguration.getInstance(context.getProject())
                                                 .MAKE_NEW_FILES_READONLY);
  }
}
