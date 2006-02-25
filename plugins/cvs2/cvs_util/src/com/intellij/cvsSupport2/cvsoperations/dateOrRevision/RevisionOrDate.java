package com.intellij.cvsSupport2.cvsoperations.dateOrRevision;

import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.Command;


/**
 * author: lesya
 */
public interface RevisionOrDate {
  RevisionOrDate EMPTY = new RevisionOrDate() {
    public void setForCommand(Command command) {
    }

    public String getRevision() {
      return "HEAD";
    }

    public CvsRevisionNumber getCvsRevisionNumber() {
      return null;
    }
  };

  void setForCommand(Command command);

  @NonNls String getRevision();

  CvsRevisionNumber getCvsRevisionNumber();
}
