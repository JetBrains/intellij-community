package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.graph.elements.Node;

public class RebaseCommand {
  public enum RebaseCommandKind {
    PICK,
    FIXUP,
    REWORD
  }

  private final RebaseCommandKind kind;
  private final Node commit;

  public RebaseCommand(RebaseCommandKind kind, Node commit) {
    this.kind = kind;
    this.commit = commit;
  }

  public RebaseCommandKind getKind() {
    return kind;
  }

  public Node getCommit() {
    return commit;
  }
}
