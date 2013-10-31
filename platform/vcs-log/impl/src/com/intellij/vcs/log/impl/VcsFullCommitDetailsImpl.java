package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.ContentRevisionFactory;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsFullCommitDetailsImpl extends VcsShortCommitDetailsImpl implements VcsFullCommitDetails {

  @NotNull private final String myFullMessage;

  @NotNull private final String myAuthorEmail;
  @NotNull private final String myCommitterName;
  @NotNull private final String myCommitterEmail;
  private final long myCommitTime;

  @NotNull private final Collection<LightChange> myChanges;

  public VcsFullCommitDetailsImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long authorTime, @NotNull VirtualFile root,
                                  @NotNull String subject, @NotNull String authorName, @NotNull String authorEmail, @NotNull String message,
                                  @NotNull String committerName, @NotNull String committerEmail, long commitTime,
                                  @NotNull List<Change> changes, @NotNull final ContentRevisionFactory contentRevisionFactory) {
    super(hash, parents, authorTime, root, subject, authorName);
    myAuthorEmail = authorEmail;
    myCommitterName = committerName;
    myCommitterEmail = committerEmail;
    myCommitTime = commitTime;
    myFullMessage = message;
    myChanges = ContainerUtil.map(changes, new Function<Change, LightChange>() {
      @Override
      public LightChange fun(Change change) {
        return LightChange.create(contentRevisionFactory, VcsFullCommitDetailsImpl.this, change);
      }
    });
  }

  @Override
  @NotNull
  public final String getFullMessage() {
    return myFullMessage;
  }

  @Override
  @NotNull
  public final Collection<Change> getChanges() {
    return ContainerUtil.map(myChanges, new Function<LightChange, Change>() {
      @Override
      public Change fun(LightChange change) {
        return change.toChange();
      }
    });
  }

  @Override
  @NotNull
  public String getAuthorEmail() {
    return myAuthorEmail;
  }

  @Override
  @NotNull
  public String getCommitterName() {
    return myCommitterName;
  }

  @Override
  @NotNull
  public String getCommitterEmail() {
    return myCommitterEmail;
  }

  @Override
  public long getCommitTime() {
    return myCommitTime;
  }

  private static class LightChange {

    private ContentRevisionFactory myContentRevisionFactory;
    private VcsFullCommitDetails myDetails;
    private boolean myModification;
    private VirtualFile myBeforeFile;
    private VirtualFile myAfterFile;
    private String myBeforePath;
    private String myAfterPath;

    private static LightChange create(ContentRevisionFactory contentRevisionFactory, VcsFullCommitDetails details, Change change) {
      LightChange lc = new LightChange();
      Change.Type type = change.getType();
      lc.myModification = type == Change.Type.MODIFICATION;
      lc.myContentRevisionFactory = contentRevisionFactory;

      ContentRevision before = change.getBeforeRevision();
      if (before != null && !lc.myModification) { // don't store the same path twice (for modification store only after path)
        FilePath filePath = before.getFile();
        if (filePath.getVirtualFile() == null) {
          lc.myBeforePath = filePath.getIOFile().getPath();
        }
        else {
          lc.myBeforeFile = filePath.getVirtualFile();
        }
      }

      ContentRevision after = change.getAfterRevision();
      if (after != null) {
        FilePath filePath = after.getFile();
        if (filePath.getVirtualFile() == null) {
          lc.myAfterPath = filePath.getIOFile().getPath();
        }
        else {
          lc.myAfterFile = filePath.getVirtualFile();
        }
      }

      lc.myDetails = details;
      return lc;
    }

    Change toChange() {
      List<Hash> parents = myDetails.getParents();
      Hash parentHash = parents.isEmpty() ? null : parents.get(0); // no parents for the initial commit

      ContentRevision before = null;
      if (parentHash != null) {
        if (myBeforeFile != null) {
          before = myContentRevisionFactory.createRevision(myBeforeFile, parentHash);
        }
        else if (myBeforePath != null) {
          before = myContentRevisionFactory.createRevision(myDetails.getRoot(), myBeforePath, parentHash);
        }
      }

      ContentRevision after = null;
      if (myAfterFile != null) {
        after = myContentRevisionFactory.createRevision(myAfterFile, myDetails.getHash());
        if (myModification && parentHash != null) { // we didn't store myBeforeFile/Path to avoid duplicate path
          before = myContentRevisionFactory.createRevision(myAfterFile, parentHash);
        }
      }
      else if (myAfterPath != null) {
        after = myContentRevisionFactory.createRevision(myDetails.getRoot(), myAfterPath, myDetails.getHash());
        if (myModification && parentHash != null) {
          before = myContentRevisionFactory.createRevision(myDetails.getRoot(), myAfterPath, parentHash);
        }
      }

      return new Change(before, after);
    }

  }
}
