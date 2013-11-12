package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogObjectsFactoryImpl implements VcsLogObjectsFactory {

  @NotNull private final VcsLogManager myLogManager;

  public VcsLogObjectsFactoryImpl(@NotNull VcsLogManager logManager) {
    myLogManager = logManager;
  }

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
                                                  @NotNull VirtualFile root, @NotNull String subject,
                                                  @NotNull String authorName, String authorEmail) {
    VcsUser author = createUser(authorName, authorEmail);
    return new VcsShortCommitDetailsImpl(hash, parents, timeStamp, root, subject, author);
  }

  @NotNull
  @Override
  public VcsFullCommitDetails createFullDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long authorTime, @NotNull VirtualFile root,
                                                @NotNull String subject, @NotNull String authorName, @NotNull String authorEmail,
                                                @NotNull String message, @NotNull String committerName,
                                                @NotNull String committerEmail, long commitTime, @NotNull List<Change> changes,
                                                @NotNull ContentRevisionFactory contentRevisionFactory) {
    VcsUser author = createUser(authorName, authorEmail);
    VcsUser committer = createUser(committerName, committerEmail);
    return new VcsFullCommitDetailsImpl(hash, parents, authorTime, root, subject, author, message, committer, commitTime,
                                        changes, contentRevisionFactory);
  }

  @NotNull
  @Override
  public VcsUser createUser(@NotNull String name, @NotNull String email) {
    VcsLogDataHolder dataHolder = myLogManager.getDataHolder();
    if (dataHolder == null) {
      return new VcsUserImpl(name, email);
    }
    return dataHolder.getUserRegistry().createUser(name, email);
  }

  @NotNull
  @Override
  public VcsRef createRef(@NotNull Hash commitHash, @NotNull String name, @NotNull VcsRefType type, @NotNull VirtualFile root) {
    return new VcsRefImpl(new NotNullFunction<Hash, Integer>() {
      @NotNull
      @Override
      public Integer fun(Hash hash) {
        return myLogManager.getDataHolder().putHash(hash);
      }
    }, commitHash, name, type, root);
  }

}
