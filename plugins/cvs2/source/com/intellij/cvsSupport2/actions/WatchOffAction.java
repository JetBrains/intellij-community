package com.intellij.cvsSupport2.actions;

import com.intellij.openapi.vcs.actions.VcsContext;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

/**
 * author: lesya
 */
public class WatchOffAction extends AbsttractWatchOnOffAction {
  protected String getTitle(VcsContext context) {
    return "Watching Off";
  }

  protected WatchMode getMode() {
    return WatchMode.OFF;
  }

}
