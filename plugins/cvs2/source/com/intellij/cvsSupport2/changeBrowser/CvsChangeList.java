/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.11.2006
 * Time: 20:44:46
 */
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.log.Revision;

import java.io.File;
import java.io.DataOutput;
import java.io.DataInput;
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

  private VirtualFile myRootFile;
  private String myRootPath;

  private final List<RevisionWrapper> myRevisions = new ArrayList<RevisionWrapper>();

  private String myUser;
  private static final int SUITABLE_DIFF = 2 * 60 * 1000;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;
  private List<Change> myChanges;
  @NonNls private static final String EXP_STATE = "Exp";
  @NonNls private static final String ADDED_STATE = "added";
  @NonNls private static final String DEAD_STATE = "dead";


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

  @Nullable
  public String getBranch() {
    if (myRevisions.size() > 0) {
      return myRevisions.get(0).getBranch();
    }
    return null;
  }

  public Collection<Change> getChanges() {
    if (myChanges == null) {
      myChanges = new ArrayList<Change>();
      for(RevisionWrapper wrapper: myRevisions) {
        final Revision revision = wrapper.getRevision();
        final String state = revision.getState();
        String path = wrapper.getFile();
        File localFile;
        if (myRootFile != null && path.startsWith(myRootPath + "/")) {
          path = path.substring(myRootPath.length()+1);
          localFile = new File(myRootFile.getPresentableUrl(), path);
        }
        else {
          localFile = new File(wrapper.getFile());
        }
        ContentRevision beforeRevision = isAdded(revision)
          ? null
          : new CvsContentRevision(new File(wrapper.getFile()), localFile,
                                   new SimpleRevision(new CvsRevisionNumber(revision.getNumber()).getPrevNumber().asString()),
                                   myEnvironment, myProject);
        ContentRevision afterRevision = (DEAD_STATE.equals(state))
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

  private static boolean isAdded(final Revision revision) {
    final String revisionState = revision.getState();
    if (EXP_STATE.equals(revisionState) && revision.getLines() == null) {
      return true;
    }
    if (ADDED_STATE.equals(revisionState)) {
      return true;
    }
    return false;
  }

  public static boolean isAncestor(final String parent, final String child) {
    return child.equals(parent) || child.startsWith(parent + "/");
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final CvsChangeList that = (CvsChangeList)o;

    if (!myRevisions.equals(that.myRevisions)) return false;

    return true;
  }

  public int hashCode() {
    return myRevisions.hashCode();
  }

  public String toString() {
    return myDescription;
  }

  public void writeToStream(DataOutput stream) throws IOException {
    stream.writeLong(myDate);
    stream.writeLong(myFinishDate);
    stream.writeLong(myNumber);
    stream.writeUTF(myDescription);
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
    int revisionCount = stream.readInt();
    for(int i=0; i<revisionCount; i++) {
      myRevisions.add(RevisionWrapper.readFromStream(stream));
    }
  }
}
