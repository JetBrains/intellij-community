/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.changeBrowser;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.log.Revision;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Date;

class RevisionWrapper implements Comparable<RevisionWrapper> {
  @NonNls private static final String ATTIC_SUFFIX = "/Attic";
  private final String myFile;
  private final Revision myRevision;
  private final String myBranch;
  private final long myTime;

  RevisionWrapper(final String file, @NotNull final Revision revision, @Nullable String branch) {
    myFile = stripAttic(file);
    myRevision = revision;
    myTime = revision.getDate().getTime();
    myBranch = branch;
  }

  @Override
  public int compareTo(final RevisionWrapper o) {
    final long diff = myTime - o.myTime;
    if (diff < 0) {
      return -1;
    } else if (diff > 0) {
      return 1;
    }
    return 0;
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

  public String getBranch() {
    return myBranch;
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
    int result = myFile.hashCode();
    result = 31 * result + myRevision.getNumber().hashCode();
    result = 31 * result + (int)(myTime ^ (myTime >>> 32));
    return result;
  }

  private static String stripAttic(final String file) {
    final int pos = file.lastIndexOf('/');
    if (pos < 0) {
      return file;
    }
    final String path = file.substring(0, pos);
    if (!path.endsWith(ATTIC_SUFFIX)) {
      return file;
    }
    return path.substring(0, path.length()-6) + file.substring(pos);
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
    stream.writeUTF(myBranch == null ? "" : myBranch);
  }

  public static RevisionWrapper readFromStream(final DataInput stream) throws IOException {
    final String file = stream.readUTF();
    final String number = stream.readUTF();
    final Revision revision = new Revision(number);
    final long time = stream.readLong();
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
    final String branch = stream.readUTF();
    return new RevisionWrapper(file, revision, branch.length() > 0 ? branch : null);
  }
}
