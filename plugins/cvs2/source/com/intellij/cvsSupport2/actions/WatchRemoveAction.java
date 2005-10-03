package com.intellij.cvsSupport2.actions;

import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.ui.WatcherDialog;
import com.intellij.openapi.vcs.actions.VcsContext;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

/**
 * author: lesya
 */
public class WatchRemoveAction extends AbstractWatchAction {
  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("operation.name.watching.remove");
  }

  protected WatcherDialog createDialog(CvsConfiguration configuration, VcsContext context) {
    return new WatcherDialog(configuration.WATCHERS.get(configuration.REMOVE_WATCH_INDEX), getTitle(context));
  }

  protected WatchMode getWatchOperation() {
    return WatchMode.REMOVE;
  }

  protected void saveWatch(CvsConfiguration configuration, Watch watch) {
    configuration.REMOVE_WATCH_INDEX = configuration.WATCHERS.indexOf(watch);
  }
}
