/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.log.Revision;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class CvsChangeList implements CommittedChangeList {
  private long myDate;
  private long myFinishDate;

  private long myNumber;
  @NotNull private String myDescription;

  private final VirtualFile myRootFile;
  private String myRootPath;

  private final List<RevisionWrapper> myRevisions = new ArrayList<>();

  private String myUser;
  public static final int SUITABLE_DIFF = 2 * 60 * 1000;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;
  private List<Change> myChanges;
  @NonNls private static final String EXP_STATE = "Exp";
  @NonNls private static final String ADDED_STATE = "added";
  @NonNls public static final String DEAD_STATE = "dead";


  public CvsChangeList(final Project project,
                       final CvsEnvironment environment,
                       @Nullable final VirtualFile rootFile,
                       final long number,
                       @NotNull final String description,
                       final long date,
                       String user,
                       String rootPath) {
    myRootFile = rootFile;
    myDate = date;
    myFinishDate = date;
    myNumber = number;
    myDescription = description;
    myUser = user;
    myRootPath = rootPath;
    myEnvironment = environment;
    myProject = project;
  }

  public CvsChangeList(final Project project,
                       final CvsEnvironment environment,
                       @Nullable final VirtualFile rootFile,
                       final DataInput stream) throws IOException {
    myProject = project;
    myEnvironment = environment;
    myRootFile = rootFile;
    readFromStream(stream);
  }

  public String getCommitterName() {
    return myUser;
  }

  public Date getCommitDate() {
    return new Date(myDate);
  }

  public long getNumber() {
    return myNumber;
  }

  public AbstractVcs getVcs() {
    return CvsVcs2.getInstance(myProject);
  }

  @Override
  public boolean isModifiable() {
    return true;
  }

  @Override
  public void setDescription(String newMessage) {
    myDescription = newMessage;
  }

  @Nullable
  public String getBranch() {
    if (myRevisions.size() > 0) {
      return myRevisions.get(0).getBranch();
    }
    return null;
  }

  public Collection<Change> getChanges() {
    if (myChanges == null) {
      myChanges = new ArrayList<>();
      for(RevisionWrapper wrapper: myRevisions) {
        final Revision revision = wrapper.getRevision();
        final String state = revision.getState();
        String path = wrapper.getFile();
        final File localFile;
        if (myRootFile != null) {
          final String directorySuffix = myRootFile.isDirectory() ? "/" : "";
          if (StringUtil.startsWithConcatenation(path, myRootPath, directorySuffix)) {
            path = path.substring(myRootPath.length() + directorySuffix.length());
            localFile = new File(myRootFile.getPresentableUrl(), path);
          }
          else {
            localFile = new File(wrapper.getFile());
          }
        }
        else {
          localFile = new File(wrapper.getFile());
        }
        final boolean added = isAdded(revision);
        final ContentRevision beforeRevision = added
          ? null
          : new CvsContentRevision(new File(wrapper.getFile()), localFile,
                                   new SimpleRevision(new CvsRevisionNumber(revision.getNumber()).getPrevNumber().asString()),
                                   myEnvironment, myProject);
        final ContentRevision afterRevision = (!added && DEAD_STATE.equals(state))
          ? null
          : new CvsContentRevision(new File(wrapper.getFile()), localFile,
                                   new SimpleRevision(revision.getNumber()),
                                   myEnvironment, myProject);
        myChanges.add(new Change(beforeRevision, afterRevision));
      }
    }
    return myChanges;
  }

  @NotNull
  public String getName() {
    return myDescription;
  }

  public String getComment() {
    return myDescription;
  }

  public boolean containsDate(long date) {
    if (date >= myDate && date <= myFinishDate) {
      return true;
    }

    if (Math.abs(date - myDate) < SUITABLE_DIFF) {
      return true;
    }

    if (Math.abs(date - myFinishDate) < SUITABLE_DIFF) {
      return true;
    }

    return false;
  }

  public boolean containsFile(final String file) {
    for(RevisionWrapper revision: myRevisions) {
      if (revision.getFile().equals(file)) {
        return true;
      }
    }
    return false;
  }

  public void addFileRevision(RevisionWrapper revision) {
    myRevisions.add(revision);
    final long revisionTime = revision.getTime();
    if (revisionTime < myDate) {
      myDate = revisionTime;
    }
    if (revisionTime > myFinishDate) {
      myFinishDate = revisionTime;
    }
  }

  public boolean containsFileRevision(RevisionWrapper revision) {
    return myRevisions.contains(revision);
  }

  private static boolean isAdded(final Revision revision) {
    final String revisionState = revision.getState();
    if (EXP_STATE.equals(revisionState) && revision.getLines() == null) {
      return true;
    }
    if (ADDED_STATE.equals(revisionState)) {
      return true;
    }
    // files added on branch
    final int[] subRevisions = new CvsRevisionNumber(revision.getNumber()).getSubRevisions();
    if (subRevisions != null && subRevisions.length > 2 && subRevisions [subRevisions.length-1] == 1) {
      return true;
    }
    return false;
  }

  public static boolean isAncestor(final String parent, final String child) {
    return child.equals(parent) || StringUtil.startsWithConcatenation(child, parent, "/");
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final CvsChangeList that = (CvsChangeList)o;

    if (!myRevisions.equals(that.myRevisions)) return false;

    return true;
  }

  public int hashCode() {
    int result = (int)(myNumber ^ (myNumber >>> 32));
    result = 31 * result + myDescription.hashCode();
    return result;
  }

  public String toString() {
    return myDescription;
  }

  public void writeToStream(DataOutput stream) throws IOException {
    stream.writeLong(myDate);
    stream.writeLong(myFinishDate);
    stream.writeLong(myNumber);
    IOUtil.writeUTFTruncated(stream, myDescription);
    stream.writeUTF(myUser);
    stream.writeUTF(myRootPath);
    stream.writeInt(myRevisions.size());
    for(RevisionWrapper revision: myRevisions) {
      revision.writeToStream(stream);
    }
  }

  private void readFromStream(final DataInput stream) throws IOException {
    myDate = stream.readLong();
    myFinishDate = stream.readLong();
    myNumber = stream.readLong();
    myDescription = stream.readUTF();
    myUser = stream.readUTF();
    myRootPath = stream.readUTF();
    final int revisionCount = stream.readInt();
    for(int i=0; i<revisionCount; i++) {
      myRevisions.add(RevisionWrapper.readFromStream(stream));
    }
  }
}
