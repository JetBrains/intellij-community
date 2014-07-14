package com.intellij.vcs.log.ui.render;

import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class GraphCommitCell {

  @NotNull private final String myText;
  @NotNull private final Collection<VcsRef> myRefsToThisCommit;

  public GraphCommitCell(@NotNull String text, @NotNull Collection<VcsRef> refsToThisCommit) {
    myText = text;
    myRefsToThisCommit = refsToThisCommit;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public Collection<VcsRef> getRefsToThisCommit() {
    return myRefsToThisCommit;
  }

}
