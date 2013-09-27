package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsShortCommitDetails;
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
  public VcsShortCommitDetails createShortDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp,
                                                  @NotNull String subject, @NotNull String authorName) {
    return new VcsShortCommitDetailsImpl(hash, parents, timeStamp, subject, authorName);
  }

  @NotNull
  @Override
  public VcsFullCommitDetails createFullDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long authorTime, @NotNull String subject,
                                                @NotNull String authorName, @NotNull String authorEmail, @NotNull String message,
                                                @NotNull String committerName,
                                                @NotNull String committerEmail, long commitTime, @NotNull List<Change> changes) {
    return new VcsFullCommitDetailsImpl(hash, parents, authorTime, subject, authorName, authorEmail, message, committerName, committerEmail,
                                        commitTime, changes);
  }
}
