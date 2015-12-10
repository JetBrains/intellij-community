package org.zmlx.hg4idea.provider;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.CommittedChangeListForRevision;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;

import java.util.Collection;
import java.util.Date;

public class HgCommittedChangeList extends CommittedChangeListForRevision {

  @NotNull private final HgVcs myVcs;
  @NotNull private String myBranch;

  public HgCommittedChangeList(@NotNull HgVcs vcs, @NotNull HgRevisionNumber revision, @NotNull String branch, String comment,
                               String committerName, Date commitDate, Collection<Change> changes) {
    super(revision.asString() + ": " + comment, comment, committerName, commitDate, changes, revision);
    myVcs = vcs;
    myBranch = StringUtil.isEmpty(branch) ? "default" : branch;
  }

  @NotNull
  @Override
  public HgRevisionNumber getRevisionNumber() {
    return (HgRevisionNumber)super.getRevisionNumber();
  }

  @NotNull
  public String getBranch() {
    return myBranch;
  }

  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  public String toString() {
    return getComment();
  }
}
