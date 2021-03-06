package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.PrintElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class GraphCommitCell {

  @NotNull private final String myText;
  @NotNull private final Collection<VcsRef> myRefsToThisCommit;
  @NotNull private final Collection<? extends PrintElement> myPrintElements;

  public GraphCommitCell(@NotNull String text,
                         @NotNull Collection<VcsRef> refsToThisCommit,
                         @NotNull Collection<? extends PrintElement> printElements) {
    myText = text;
    myRefsToThisCommit = refsToThisCommit;
    myPrintElements = printElements;
  }

  @NotNull
  @NlsSafe
  public String getText() {
    return myText;
  }

  @NotNull
  public Collection<VcsRef> getRefsToThisCommit() {
    return myRefsToThisCommit;
  }

  @NotNull
  public Collection<? extends PrintElement> getPrintElements() {
    return myPrintElements;
  }

  @Override
  public String toString() {
    return myText;
  }
}
