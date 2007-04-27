package com.intellij.cvsSupport2.changeBrowser;

import org.netbeans.lib.cvsclient.command.log.Revision;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;
import java.io.DataInput;
import java.util.Date;

class RevisionWrapper implements Comparable<RevisionWrapper> {
  private final String myFile;
  private final Revision myRevision;
  private final long myTime;

  public RevisionWrapper(final String file, @NotNull final Revision revision) {
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

  public void writeToStream(final DataOutput stream) throws IOException {
    stream.writeUTF(myFile);
    stream.writeUTF(myRevision.getNumber());
    stream.writeLong(myRevision.getDate().getTime());
    stream.writeUTF(myRevision.getAuthor());
    stream.writeUTF(myRevision.getState());
    final String lines = myRevision.getLines();
    stream.writeUTF(lines == null ? "" : lines);
    stream.writeUTF(myRevision.getMessage());
    final String branches = myRevision.getBranches();
    stream.writeUTF(branches == null ? "" : branches);
  }


  public static RevisionWrapper readFromStream(final DataInput stream) throws IOException {
    String file = stream.readUTF();
    String number = stream.readUTF();
    Revision revision = new Revision(number);
    long time = stream.readLong();
    revision.setDate(new Date(time));
    revision.setAuthor(stream.readUTF());
    revision.setState(stream.readUTF());
    final String lines = stream.readUTF();
    if (lines.length() > 0) {
      revision.setLines(lines);
    }
    revision.setMessage(stream.readUTF());
    final String branches = stream.readUTF();
    if (branches.length() > 0) {
      revision.setBranches(branches);
    }
    return new RevisionWrapper(file, revision);
  }
}
