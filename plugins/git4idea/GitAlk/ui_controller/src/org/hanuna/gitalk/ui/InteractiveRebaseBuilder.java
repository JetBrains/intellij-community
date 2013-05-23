package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.refs.Ref;

import java.util.List;

public class InteractiveRebaseBuilder {
  public static final InteractiveRebaseBuilder EMPTY = new InteractiveRebaseBuilder();

  void startRebase(Ref subjectRef, Node onto) {

  }

  void startRebaseOnto(Ref subjectRef, Node base, List<Node> nodesToRebase) {

  }

  void moveCommits(Ref subjectRef, Node base, InsertPosition position, List<Node> nodesToInsert) {

  }

  void fixUp(Ref subjectRef, Node target, List<Node> nodesToFixUp) {

  }

  enum InsertPosition {
    ABOVE,
    BELOW
  }
}
