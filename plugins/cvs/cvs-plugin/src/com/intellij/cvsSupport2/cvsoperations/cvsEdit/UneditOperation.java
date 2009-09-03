package com.intellij.cvsSupport2.cvsoperations.cvsEdit;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.reservedcheckout.UneditCommand;

/**
 * author: lesya
 */
public class UneditOperation extends CvsOperationOnFiles{
  private final boolean myMakeNewFilesReadOnly;

  public UneditOperation(boolean makeNewFilesReadOnly) {
    myMakeNewFilesReadOnly = makeNewFilesReadOnly;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    UneditCommand result = new UneditCommand();
    result.setTemporaryWatch(Watch.TALL);
    addFilesToCommand(root, result);
    return result;
  }

  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setCheckedOutFilesReadOnly(myMakeNewFilesReadOnly);
  }

  protected String getOperationName() {
    return "unedit";
  }
}
