package com.intellij.cvsSupport2.cvsoperations.cvsWatch;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.watch.WatchCommand;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

/**
 * author: lesya
 */
public class WatchOperation extends CvsOperationOnFiles{
  private final WatchMode myWatchMode;
  private final Watch myWatch;

  public WatchOperation(WatchMode watchMode) {
    this(watchMode, Watch.ALL);
  }

  public WatchOperation(WatchMode watchMode, Watch watch) {
    myWatchMode = watchMode;
    myWatch = watch;
  }

  protected String getOperationName() {
    return "watch";
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    WatchCommand result = new WatchCommand();
    result.setWatchMode(myWatchMode);
    result.setWatch(myWatch);
    addFilesToCommand(root, result);
    return result;
  }
}