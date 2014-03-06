package com.intellij.vcs.log.graph.render;

import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


/**
 * @author erokhins
 */
public class GraphCommitCell extends CommitCell {

  public GraphCommitCell(@NotNull String text, @NotNull Collection<VcsRef> refsToThisCommit) {
    super(text, refsToThisCommit);
  }

}
