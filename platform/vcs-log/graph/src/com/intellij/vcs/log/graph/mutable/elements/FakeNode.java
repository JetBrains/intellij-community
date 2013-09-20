package com.intellij.vcs.log.graph.mutable.elements;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.RebaseCommand;
import com.intellij.vcs.log.graph.elements.Branch;
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
