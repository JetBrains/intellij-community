package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.vcs.actions.VcsContext;

/**
 * author: lesya
 */
public class UneditAction extends AsbtractActionFromEditGroup {
  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("operation.name.unedit");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    return CommandCvsHandler.createUneditHandler(context.getSelectedFiles(),
                                                 CvsConfiguration.getInstance(context.getProject())
                                                 .MAKE_NEW_FILES_READONLY);
  }
}
