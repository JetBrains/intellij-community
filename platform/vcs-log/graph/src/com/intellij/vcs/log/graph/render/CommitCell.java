package com.intellij.vcs.log.graph.render;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author erokhins
 */
public class CommitCell {

  private final String text;
  private final Collection<VcsRef> refsToThisCommit;
  private Hash myHash;

  /**
   * Hash can be null, if, for example, this is a cell which doesn't contain a commit, but contains only a part of the graph
   * (such situations may appear, for example, if graph is filtered by branch, as described in IDEA-115442).
   */
  public CommitCell(@Nullable Hash hash, @NotNull String text, @NotNull Collection<VcsRef> refsToThisCommit) {
    myHash = hash;
    this.text = text;
    this.refsToThisCommit = refsToThisCommit;
  }

  public String getText() {
    return text;
  }

  public Collection<VcsRef> getRefsToThisCommit() {
    return refsToThisCommit;
  }

  @Nullable
  public Hash getHash() {
    return myHash;
  }
}
