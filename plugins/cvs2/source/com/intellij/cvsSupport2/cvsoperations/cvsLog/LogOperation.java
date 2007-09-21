package com.intellij.cvsSupport2.cvsoperations.cvsLog;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.BranchesProvider;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.vcs.FilePath;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.log.LogCommand;
import org.netbeans.lib.cvsclient.command.log.LogInformation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * author: lesya
 */
public class LogOperation extends CvsOperationOnFiles implements BranchesProvider{
  private final List<LogInformation> myLogInformationList = new ArrayList<LogInformation>();

  public LogOperation(Collection<FilePath> files){
    for (final FilePath file : files) {
      addFile(file.getIOFile());
    }
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    LogCommand command = new LogCommand();
    addFilesToCommand(root, command);
    command.setHeaderOnly(true);
    return command;
  }

  public void fileInfoGenerated(Object info) {
    super.fileInfoGenerated(info);
    if (info instanceof LogInformation) {
      myLogInformationList.add((LogInformation)info);
    }
  }

  public Collection<String> getAllBranches() {
    return TagsHelper.getAllBranches(myLogInformationList);
  }

  public Collection<CvsRevisionNumber> getAllRevisions() {
    return null;
  }

  protected String getOperationName() {
    return "log";
  }

  public int getFilesToProcessCount() {
    return -1;
  }
}
