package com.intellij.vcs.log.graph.render;

import com.intellij.vcs.log.VcsRef;

import java.util.Collection;

/**
 * @author erokhins
 */
public class CommitCell {

  private final String text;
  private final Collection<VcsRef> refsToThisCommit;

  public CommitCell(String text, Collection<VcsRef> refsToThisCommit) {
    this.text = text;
    this.refsToThisCommit = refsToThisCommit;
  }

  public String getText() {
    return text;
  }

  public Collection<VcsRef> getRefsToThisCommit() {
    return refsToThisCommit;
  }

}
