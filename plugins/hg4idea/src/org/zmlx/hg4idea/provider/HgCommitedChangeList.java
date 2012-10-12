package org.zmlx.hg4idea.provider;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;

import java.util.Collection;
import java.util.Date;

public class HgCommitedChangeList extends CommittedChangeListImpl {

  @NotNull private final HgVcs myVcs;
  private HgRevisionNumber revision;

  public HgCommitedChangeList(@NotNull HgVcs vcs, HgRevisionNumber revision, String comment, String committerName, Date commitDate,
                              Collection<Change> changes) {
    super(revision.asString() + ": " + comment, comment, committerName, revision.getRevisionAsLong(), commitDate, changes);
    myVcs = vcs;
    this.revision = revision;
  }

  public HgRevisionNumber getRevision() {
    return revision;
  }

  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

}
