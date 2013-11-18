package com.intellij.vcs.log;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Use this factory to create correct instances of such commonly used vcs-log-api objects as {@link Hash} or {@link VcsShortCommitDetails}.
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogObjectsFactory {

  @NotNull
  Hash createHash(@NotNull String stringHash);

  @NotNull
  VcsCommit createCommit(@NotNull Hash hash, @NotNull List<Hash> parents);

  @NotNull
  TimedVcsCommit createTimedCommit(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp);

  @NotNull
  VcsShortCommitDetails createShortDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp,
                                           VirtualFile root, @NotNull String subject, @NotNull String authorName, String authorEmail);

  @NotNull
  VcsFullCommitDetails createFullDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long time, VirtualFile root,
                                         @NotNull String subject,
                                         @NotNull String authorName, @NotNull String authorEmail, @NotNull String message,
                                         @NotNull String committerName,
                                         @NotNull String committerEmail, long authorTime, @NotNull List<Change> changes,
                                         @NotNull ContentRevisionFactory contentRevisionFactory);

  @NotNull
  VcsUser createUser(@NotNull String name, @NotNull String email);

  @NotNull
  VcsRef createRef(@NotNull Hash commitHash, @NotNull String name, @NotNull VcsRefType type, @NotNull VirtualFile root);

}
