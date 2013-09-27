package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>Returns the basic level of commit meta-data: author, time, subject.</p>
 *
 * <p>These details will be displayed in the log table.</p>
 *
 * <p>An instance of this object can be obtained via
 *    {@link VcsLogObjectsFactory#createShortDetails(Hash, List, long, String, String) VcsLogObjectsFactory#createShortDetails}</p>
 *
 * @see VcsFullCommitDetails
 * @author Kirill Likhodedov
 */
public interface VcsShortCommitDetails {

  @NotNull
  Hash getHash();

  @NotNull
  List<Hash> getParents();

  long getAuthorTime();

  @NotNull
  String getSubject();

  @NotNull
  String getAuthorName();

}
