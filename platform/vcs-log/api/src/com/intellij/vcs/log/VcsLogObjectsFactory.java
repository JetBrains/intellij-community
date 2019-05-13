package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Use this factory to create correct instances of such commonly used intellij.platform.vcs.log objects as {@link Hash} or {@link VcsShortCommitDetails}.
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogObjectsFactory {

  @NotNull
  Hash createHash(@NotNull String stringHash);

  @NotNull
  TimedVcsCommit createTimedCommit(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp);

  @NotNull
  VcsShortCommitDetails createShortDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime,
                                           VirtualFile root, @NotNull String subject, @NotNull String authorName, String authorEmail,
                                           @NotNull String committerName, @NotNull String committerEmail, long authorTime);

  @NotNull
  VcsCommitMetadata createCommitMetadata(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, VirtualFile root,
                                         @NotNull String subject, @NotNull String authorName, @NotNull String authorEmail,
                                         @NotNull String message, @NotNull String committerName, @NotNull String committerEmail,
                                         long authorTime);

  @NotNull
  VcsUser createUser(@NotNull String name, @NotNull String email);

  @NotNull
  VcsRef createRef(@NotNull Hash commitHash, @NotNull String name, @NotNull VcsRefType type, @NotNull VirtualFile root);
}
