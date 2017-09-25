package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Returns the basic level of commit meta-data: author, time, subject. These details will be displayed in the log table.
 * <p/>
 * An instance of this object can be obtained via
 * {@link VcsLogObjectsFactory#createShortDetails(Hash, List, long, VirtualFile, String, String, String, String, String, long)
 * VcsLogObjectsFactory#createShortDetails}.
 * <p/>
 * It is not recommended to create a custom implementation of this interface, but if you need it, <b>make sure to implement {@code equals()}
 * and {@code hashcode()} so that they consider only the Hash</b>, i.e. two VcsShortCommitDetails are equal if and only if they have equal
 * hash codes. The VCS Log framework heavily relies on this fact.
 *
 * @see VcsCommitMetadata
 * @see VcsFullCommitDetails
 */
public interface VcsShortCommitDetails extends TimedVcsCommit {

  @NotNull
  VirtualFile getRoot();

  @NotNull
  String getSubject();

  @NotNull
  VcsUser getAuthor();

  @NotNull
  VcsUser getCommitter();

  long getAuthorTime();

  long getCommitTime();
}
