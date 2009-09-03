package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.WatchOperation;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

public abstract class AbstractWatchOnOffAction extends AbstractActionFromEditGroup {
  protected abstract String getTitle(VcsContext context);

  protected CvsHandler getCvsHandler(CvsContext context) {
    WatchOperation watchOperation = new WatchOperation(getMode());
    VirtualFile[] files = context.getSelectedFiles();
    for (VirtualFile file : files) {
      watchOperation.addFile(file);
    }
    return new CommandCvsHandler(getTitle(context), watchOperation);
  }

  protected abstract WatchMode getMode();
}