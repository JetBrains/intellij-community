package com.intellij.vcs.log.data.rebase;

import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.RebaseCommand;

import java.util.Collections;
import java.util.List;

public class InteractiveRebaseBuilder {
  public static final InteractiveRebaseBuilder EMPTY = new InteractiveRebaseBuilder();

  public void startRebase(VcsRef subjectRef, Node onto) {

  }

  public void startRebaseOnto(VcsRef subjectRef, Node base, List<Node> nodesToRebase) {

  }

  public void moveCommits(VcsRef subjectRef, Node base, InsertPosition position, List<Node> nodesToInsert) {

  }

  public void fixUp(VcsRef subjectRef, Node target, List<Node> nodesToFixUp) {

  }

  public void reword(VcsRef subjectRef, Node commitToReword, String newMessage) {

  }

  public List<RebaseCommand> getRebaseCommands() {
    return Collections.emptyList();
  }

  public enum InsertPosition {
    ABOVE,
    BELOW
  }
}
