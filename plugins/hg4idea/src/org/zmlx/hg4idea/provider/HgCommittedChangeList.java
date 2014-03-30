package org.zmlx.hg4idea.provider;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vcs.versionBrowser.VcsRevisionNumberAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;

import java.util.Collection;
import java.util.Date;

public class HgCommittedChangeList extends CommittedChangeListImpl implements VcsRevisionNumberAware {

  @NotNull private final HgVcs myVcs;
  @NotNull private HgRevisionNumber myRevision;
  @NotNull private String myBranch;

  public HgCommittedChangeList(@NotNull HgVcs vcs, @NotNull HgRevisionNumber revision, @NotNull String branch, String comment,
                               String committerName, Date commitDate, Collection<Change> changes) {
    super(revision.asString() + ": " + comment, comment, committerName, revision.getRevisionAsLong(), commitDate, changes);
    myVcs = vcs;
    myRevision = revision;
    myBranch = StringUtil.isEmpty(branch) ? "default" : branch;
  }

  @NotNull
  public HgRevisionNumber getRevision() {
    return myRevision;
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

  @NotNull
  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return myRevision;
  }

}
