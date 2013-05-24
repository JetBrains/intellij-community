package org.hanuna.gitalk.log.commit.parents;

import org.hanuna.gitalk.commit.Hash;

public class RebaseCommand {
  public enum RebaseCommandKind {
    PICK,
    FIXUP
  }

  private final RebaseCommandKind kind;
  private final Hash commit;

  public RebaseCommand(RebaseCommandKind kind, Hash commit) {
    this.kind = kind;
    this.commit = commit;
  }

  public RebaseCommandKind getKind() {
    return kind;
  }

  public Hash getCommit() {
    return commit;
  }
}
