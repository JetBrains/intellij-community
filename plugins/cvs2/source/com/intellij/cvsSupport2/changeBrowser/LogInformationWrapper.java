package com.intellij.cvsSupport2.changeBrowser;

import org.netbeans.lib.cvsclient.command.log.Revision;

import java.util.List;

public class LogInformationWrapper {
  private final String myFile;
  private final List<Revision> myRevisions;

  public LogInformationWrapper(final String file, final List<Revision> revisions) {
    myFile = file;
    myRevisions = revisions;
  }

  public String getFile() {
    return myFile;
  }

  public List<Revision> getRevisions() {
    return myRevisions;
  }
}
