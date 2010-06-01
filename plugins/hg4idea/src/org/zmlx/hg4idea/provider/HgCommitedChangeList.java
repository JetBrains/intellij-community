package org.zmlx.hg4idea.provider;

import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.versionBrowser.*;
import org.zmlx.hg4idea.*;

import java.util.*;

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
