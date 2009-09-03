package com.intellij.cvsSupport2.cvsoperations.cvsStatus;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.status.StatusCommand;
import org.netbeans.lib.cvsclient.command.status.StatusInformation;
import org.netbeans.lib.cvsclient.file.FileStatus;

import java.io.File;
import java.util.Collection;

public class StatusOperation extends CvsOperationOnFiles {
  private static final String ourNoneTag = "(none)";

  private String myRepositoryRevision;
  private FileStatus myStatus;
  private String myStickyDate;
  private String myStickyTag;

  public StatusOperation(Collection<File> files) {
    for (final File file : files) {
      addFile(file);
    }
  }

  protected Command createCommand(final CvsRootProvider root, final CvsExecutionEnvironment cvsExecutionEnvironment) {
    final StatusCommand command = new StatusCommand();
    addFilesToCommand(root, command);
    return command;
  }

  protected String getOperationName() {
    return "status";
  }

  public void fileInfoGenerated(Object info) {
    super.fileInfoGenerated(info);

    if (info instanceof StatusInformation) {
      final StatusInformation statusInformation = (StatusInformation) info;
      myRepositoryRevision = statusInformation.getRepositoryRevision();
      myStatus = statusInformation.getStatus();
      myStickyDate = statusInformation.getStickyDate();
      myStickyDate = ourNoneTag.equals(myStickyDate) ? null : myStickyDate;
      myStickyTag = statusInformation.getStickyTag();
      myStickyTag = ourNoneTag.equals(myStickyTag) ? null : myStickyTag;
    }
  }

  public String getRepositoryRevision() {
    return myRepositoryRevision;
  }

  public FileStatus getStatus() {
    return myStatus;
  }

  public String getStickyDate() {
    return myStickyDate;
  }

  public String getStickyTag() {
    return myStickyTag;
  }
}
