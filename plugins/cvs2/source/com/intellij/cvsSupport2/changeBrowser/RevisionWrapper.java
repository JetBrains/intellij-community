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


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RevisionWrapper that = (RevisionWrapper)o;

    if (myTime != that.myTime) return false;
    if (!myFile.equals(that.myFile)) return false;
    if (!myRevision.getNumber().equals(that.myRevision.getNumber())) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myFile.hashCode();
    result = 31 * result + myRevision.getNumber().hashCode();
    result = 31 * result + (int)(myTime ^ (myTime >>> 32));
    return result;
  }
}
