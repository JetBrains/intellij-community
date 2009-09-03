package com.intellij.cvsSupport2.cvsoperations.cvsCommit;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.commit.CommitCommand;

public class CommitFilesOperation extends CvsOperationOnFiles {
  private Object myMessage;
  private final boolean myMakeNewFilesReadOnly;

  public CommitFilesOperation(String message, boolean makeNewFilesReadOnly) {
    myMessage = message;
    myMakeNewFilesReadOnly = makeNewFilesReadOnly;
  }

  public CommitFilesOperation(boolean makeNewFilesReadOnly) {
    this(null, makeNewFilesReadOnly);
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    CommitCommand result = new CommitCommand();
    addFilesToCommand(root, result);
    result.setForceCommit(true);
    if(myMessage != null)
      result.setMessage(myMessage.toString());
    return result;
  }

  public void setMessage(Object parameters) {
    myMessage = parameters;
  }

  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setCheckedOutFilesReadOnly(myMakeNewFilesReadOnly);
  }

  protected String getOperationName() {
    return "commit";
  }

  public int getFilesToProcessCount() {
    return CvsHandler.UNKNOWN_COUNT;
  }

  @Override
  public boolean runInReadThread() {
    return false;
  }
}
