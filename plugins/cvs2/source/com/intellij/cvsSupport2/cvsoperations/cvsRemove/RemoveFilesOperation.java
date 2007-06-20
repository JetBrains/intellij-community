package com.intellij.cvsSupport2.cvsoperations.cvsRemove;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.remove.RemoveCommand;

public class RemoveFilesOperation extends CvsOperationOnFiles {
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    RemoveCommand result = new RemoveCommand();
    addFilesToCommand(root, result);
    return result;
  }

  protected String getOperationName() {
    return "remove";
  }

  public boolean runInReadThread() {
    return false;
  }
}
