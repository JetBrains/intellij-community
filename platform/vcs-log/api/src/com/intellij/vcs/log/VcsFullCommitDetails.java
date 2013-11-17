package com.intellij.vcs.log;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * <p>Full details of a commit: all metadata (commit message, author, committer, etc.) and the changes.</p>
 * <p>These details will be shown in dedicated panels displayed near the log.</p>
 * <p>An instance of this object can be obtained via
 *    {@link VcsLogObjectsFactory#createFullDetails(Hash, List, long, VirtualFile, String, String, String, String, String, String, long,
 *    List, ContentRevisionFactory) VcsLogObjectsFactory#createFullDetails}</p>
 *
 * @author Kirill Likhodedov
 */
public interface VcsFullCommitDetails extends VcsShortCommitDetails {

  @NotNull
  String getFullMessage();

  @NotNull
  Collection<Change> getChanges();

  @NotNull
  VcsUser getCommitter();

  long getAuthorTime();

}
