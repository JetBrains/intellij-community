package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * author: lesya
 */
public class RestoreFileAction extends ActionOnSelectedElement {
  protected final VirtualFile myParent;
  private final String myFileName;

  public RestoreFileAction(VirtualFile parent, String fileName) {
    super(true);
    getVisibility().shouldNotBePerformedOnDirectory();
    myParent = parent;
    myFileName = fileName;
  }

  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("operation.name.restore.file");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    return CommandCvsHandler.createRestoreFileHandler(myParent,
                                                      myFileName,
                                                      CvsConfiguration.getInstance(context.getProject()).MAKE_NEW_FILES_READONLY);
  }

  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    final List<VcsException> errors = handler.getErrors();
    if (errors == null || (errors != null && errors.isEmpty())) {
      CvsEntriesManager.getInstance().clearCachedEntriesFor(myParent);
    }
  }

}
