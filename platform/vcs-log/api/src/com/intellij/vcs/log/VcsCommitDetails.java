package com.intellij.vcs.log;

import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Full details of a commit: all metadata (commit message, author, committer, etc.) and the changes.
 *
 * @author Kirill Likhodedov
 */
public class VcsCommitDetails extends VcsCommitMiniDetails {

  @NotNull private final String myFullMessage;

  @NotNull private final String myAuthorEmail;
  @NotNull private final String myCommitterName;
  @NotNull private final String myCommitterEmail;
  private final long myCommitTime;

  @NotNull private final Collection<Change> myChanges;

  public VcsCommitDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long authorTime, @NotNull String subject,
                          @NotNull String authorName, @NotNull String authorEmail, @NotNull String message, @NotNull String committerName,
                          @NotNull String committerEmail, long commitTime, @NotNull List<Change> changes) {
    super(hash, parents, authorTime, subject, authorName);
    myAuthorEmail = authorEmail;
    myCommitterName = committerName;
    myCommitterEmail = committerEmail;
    myCommitTime = commitTime;
    myFullMessage = message;
    myChanges = changes;
  }

  @NotNull
  public final String getFullMessage() {
    return myFullMessage;
  }

  @NotNull
  public final Collection<Change> getChanges() {
    return myChanges;
  }

  @NotNull
  public String getAuthorEmail() {
    return myAuthorEmail;
  }

  @NotNull
  public String getCommitterName() {
    return myCommitterName;
  }

  @NotNull
  public String getCommitterEmail() {
    return myCommitterEmail;
  }

  public long getCommitTime() {
    return myCommitTime;
  }
}
