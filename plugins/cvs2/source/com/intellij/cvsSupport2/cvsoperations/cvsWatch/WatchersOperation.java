package com.intellij.cvsSupport2.cvsoperations.cvsWatch;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.watchers.WatchersCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class WatchersOperation extends CvsOperationOnFiles{
  private final List<WatcherInfo> myWatchers = new ArrayList<WatcherInfo>();
  public WatchersOperation(VirtualFile[] files){
    for (int i = 0; i < files.length; i++) {
      addFile(files[i]);
    }

  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    WatchersCommand result = new WatchersCommand();
    addFilesToCommand(root, result);
    return result;
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    super.messageSent(message, byteMessage, error, tagged);
    if (!error && !tagged){
      WatcherInfo info = WatcherInfo.createOn(message);
      if (info != null) myWatchers.add(info);
    }
  }

  protected String getOperationName() {
    return "watchers";
  }

  public List<WatcherInfo> getWatchers(){
    return myWatchers;
  }

}
