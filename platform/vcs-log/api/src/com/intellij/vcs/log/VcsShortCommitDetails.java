package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>Returns the basic level of commit meta-data: author, time, subject.</p>
 *
 * <p>These details will be displayed in the log table.</p>
 *
 * <p>An instance of this object can be obtained via
 *    {@link VcsLogObjectsFactory#createShortDetails(Hash, List, long, VirtualFile, String, String, String)
 *    VcsLogObjectsFactory#createShortDetails}
 * </p>
 *
 * @see VcsCommitMetadata
 * @see VcsFullCommitDetails
 */
public interface VcsShortCommitDetails extends TimedVcsCommit {

  @Override
  @NotNull
  Hash getHash();

  @NotNull
  VirtualFile getRoot();

  @Override
  @NotNull
  List<Hash> getParents();

  @Override
  long getTime();

  @NotNull
  String getSubject();

  @NotNull
  VcsUser getAuthor();

}
