package org.zmlx.hg4idea.provider;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;

import java.util.Collection;
import java.util.Date;

public class HgCommittedChangeList extends CommittedChangeListImpl {

  @NotNull private final HgVcs myVcs;
  @NotNull private HgRevisionNumber myRevision;

  public HgCommittedChangeList(@NotNull HgVcs vcs, @NotNull HgRevisionNumber revision, String comment, String committerName,
                               Date commitDate, Collection<Change> changes) {
    super(revision.asString() + ": " + comment, comment, committerName, revision.getRevisionAsLong(), commitDate, changes);
    myVcs = vcs;
    myRevision = revision;
  }

  @NotNull
  public HgRevisionNumber getRevision() {
    return myRevision;
  }

  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

}
