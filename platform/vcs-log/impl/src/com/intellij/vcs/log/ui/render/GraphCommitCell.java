// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.ui.VcsBookmarkRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class GraphCommitCell {

  private final @NotNull String myText;
  private final @NotNull Collection<VcsRef> myRefsToThisCommit;
  private final @NotNull Collection<VcsBookmarkRef> myBookmarksToThisCommit;
  private final @NotNull Collection<? extends PrintElement> myPrintElements;

  public GraphCommitCell(@NotNull String text,
                         @NotNull Collection<VcsRef> refsToThisCommit,
                         @NotNull Collection<VcsBookmarkRef> bookmarksToThisCommit,
                         @NotNull Collection<? extends PrintElement> printElements) {
    myText = text;
    myRefsToThisCommit = refsToThisCommit;
    myBookmarksToThisCommit = bookmarksToThisCommit;
    myPrintElements = printElements;
  }

  public @NotNull @NlsSafe String getText() {
    return myText;
  }

  public @NotNull Collection<VcsRef> getRefsToThisCommit() {
    return myRefsToThisCommit;
  }

  public @NotNull Collection<VcsBookmarkRef> getBookmarksToThisCommit() {
    return myBookmarksToThisCommit;
  }

  public @NotNull Collection<? extends PrintElement> getPrintElements() {
    return myPrintElements;
  }

  @Override
  public String toString() {
    return myText;
  }
}
