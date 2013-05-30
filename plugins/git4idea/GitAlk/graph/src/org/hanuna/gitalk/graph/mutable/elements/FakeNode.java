package org.hanuna.gitalk.graph.mutable.elements;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.log.commit.parents.RebaseCommand;
import org.jetbrains.annotations.NotNull;

public class FakeNode extends MutableNode {
  private final RebaseCommand myCommand;

  public FakeNode(Branch branch, Hash hash, RebaseCommand command) {
    super(branch, hash);
    myCommand = command;
  }

  @NotNull
  public RebaseCommand getCommand() {
    return myCommand;
  }
}
