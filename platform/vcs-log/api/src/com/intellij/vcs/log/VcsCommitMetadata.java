package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>Full details of a commit: all metadata (commit message, author, committer, etc.) but without changes.</p>
 * <p>These details will be shown in dedicated panels displayed near the log, and can be used for in-memory filtering.</p>
 * <p>An instance of this object can be obtained via
 *    {@link VcsLogObjectsFactory#createCommitMetadata(Hash, List, long, VirtualFile, String, String, String, String, String, String,long)
 *    VcsLogObjectsFactory#createMediumDetails}</p>
 */
public interface VcsCommitMetadata extends VcsShortCommitDetails {

  @NotNull
  String getFullMessage();

  @NotNull
  VcsUser getCommitter();

  long getAuthorTime();

}
