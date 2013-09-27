package com.intellij.vcs.log;

import com.intellij.openapi.vcs.changes.Change;
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
  VcsShortCommitDetails createShortDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp,
                                           @NotNull String subject, @NotNull String authorName);

  @NotNull
  VcsFullCommitDetails createFullDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long authorTime, @NotNull String subject,
                                         @NotNull String authorName, @NotNull String authorEmail, @NotNull String message,
                                         @NotNull String committerName,
                                         @NotNull String committerEmail, long commitTime, @NotNull List<Change> changes);
}
