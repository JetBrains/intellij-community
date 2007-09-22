package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.WatchOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.ui.WatcherDialog;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

public abstract class AbstractWatchAction extends AbstractActionFromEditGroup {
  protected CvsHandler getCvsHandler(CvsContext context) {
    CvsConfiguration configuration = CvsConfiguration.getInstance(context.getProject());
    WatcherDialog dialog = createDialog(configuration, context);
    dialog.show();
    if (!dialog.isOK()) return CvsHandler.NULL;
    Watch watch = dialog.getWatch();
    saveWatch(configuration, watch);
    WatchOperation watchOperation = new WatchOperation(getWatchOperation(), watch);
    VirtualFile[] files = context.getSelectedFiles();
    for (int i = 0; i < files.length; i++) {
      watchOperation.addFile(files[i]);
    }
    return new CommandCvsHandler(getTitle(context), watchOperation);
  }

  protected abstract WatcherDialog createDialog(CvsConfiguration configuration, VcsContext context);

  protected abstract WatchMode getWatchOperation();

  protected abstract void saveWatch(CvsConfiguration configuration, Watch watch);
}