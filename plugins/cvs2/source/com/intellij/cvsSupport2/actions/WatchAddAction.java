package com.intellij.cvsSupport2.actions;

import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.ui.WatcherDialog;
import com.intellij.CvsBundle;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

/**
 * author: lesya
 */
public class WatchAddAction extends AbstractWatchAction{
  protected String getTitle(VcsContext context) {
    return CvsBundle.getAddWatchingOperationName();
  }

  protected WatcherDialog createDialog(CvsConfiguration configuration, VcsContext context) {
    return new WatcherDialog(configuration.WATCHERS.get(configuration.ADD_WATCH_INDEX), CvsBundle.getAddWatchingOperationName());
  }

  protected WatchMode getWatchOperation() {
    return WatchMode.ADD;
  }

  protected void saveWatch(CvsConfiguration configuration, Watch watch) {
    configuration.ADD_WATCH_INDEX = configuration.WATCHERS.indexOf(watch);
  }
}
