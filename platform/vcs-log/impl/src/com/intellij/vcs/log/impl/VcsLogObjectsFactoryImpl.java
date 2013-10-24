package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogObjectsFactoryImpl implements VcsLogObjectsFactory {

  @NotNull
  @Override
  public Hash createHash(@NotNull String stringHash) {
    return HashImpl.build(stringHash);
  }

  @NotNull
  @Override
  public VcsCommit createCommit(@NotNull Hash hash, @NotNull List<Hash> parents) {
    return new VcsCommitImpl(hash, parents);
  }

  @NotNull
  @Override
  public TimedVcsCommit createTimedCommit(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp) {
    return new TimedVcsCommitImpl(hash, parents, timeStamp);
  }

  @NotNull
  @Override
  public VcsShortCommitDetails createShortDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp,
                                                  @NotNull VirtualFile root, @NotNull String subject, @NotNull String authorName) {
    return new VcsShortCommitDetailsImpl(hash, parents, timeStamp, root, subject, authorName);
  }

  @NotNull
  @Override
  public VcsFullCommitDetails createFullDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long authorTime, @NotNull VirtualFile root,
                                                @NotNull String subject, @NotNull String authorName, @NotNull String authorEmail,
                                                @NotNull String message, @NotNull String committerName,
                                                @NotNull String committerEmail, long commitTime, @NotNull List<Change> changes) {
    return new VcsFullCommitDetailsImpl(hash, parents, authorTime, root, subject, authorName, authorEmail, message, committerName,
                                        committerEmail, commitTime, changes);
  }
}
