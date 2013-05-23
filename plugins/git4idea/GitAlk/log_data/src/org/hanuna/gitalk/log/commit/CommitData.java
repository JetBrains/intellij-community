package org.hanuna.gitalk.log.commit;

import git4idea.history.browser.GitCommit;
import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitData {

  private final GitCommit myCommit;

  public CommitData(GitCommit commit) {
    myCommit = commit;
  }

  public Hash getCommitHash() {
    return Hash.build(myCommit.getShortHash().getString());
  }

  @NotNull
  public String getMessage() {
    return myCommit.getDescription();
  }

  @NotNull
  public String getAuthor() {
    return myCommit.getAuthor();
  }

  public long getTimeStamp() {
    return myCommit.getAuthorTime();
  }

  public GitCommit getFullCommit() {
    return myCommit;
  }
}
