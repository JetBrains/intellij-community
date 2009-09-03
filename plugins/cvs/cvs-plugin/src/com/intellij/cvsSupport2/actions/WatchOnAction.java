package com.intellij.cvsSupport2.actions;

import com.intellij.openapi.vcs.actions.VcsContext;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

/**
 * author: lesya
 */
public class WatchOnAction extends AbstractWatchOnOffAction {
  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("operation.name.watching.on");
  }

  protected WatchMode getMode() {
    return WatchMode.ON;
  }
}
