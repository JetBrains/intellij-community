package org.zmlx.hg4idea.provider;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import org.zmlx.hg4idea.HgRevisionNumber;

import java.util.Collection;
import java.util.Date;

public class HgCommitedChangeList extends CommittedChangeListImpl {
  private HgRevisionNumber revision;

  public HgCommitedChangeList(HgRevisionNumber revision, String comment, String committerName, Date commitDate, Collection<Change> changes) {
    super(revision.asString() + ": " + comment, comment, committerName, revision.getRevisionAsLong(), commitDate, changes);
    this.revision = revision;
  }

  public HgRevisionNumber getRevision() {
    return revision;
  }
}
