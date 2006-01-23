package com.intellij.cvsSupport2.changeBrowser;

import org.netbeans.lib.cvsclient.command.log.Revision;

class RevisionWrapper implements Comparable<RevisionWrapper> {
  private final String myFile;
  private final Revision myRevision;
  private final long myTime;

  public RevisionWrapper(final String file, final Revision revision) {
    myFile = file;
    myRevision = revision;
    myTime = revision.getDate().getTime();
  }

  public int compareTo(final RevisionWrapper o) {
    return (int)(myTime - o.myTime);
  }

  public String getFile() {
    return myFile;
  }

  public Revision getRevision() {
    return myRevision;
  }

  public long getTime() {
    return myTime;
  }
}
