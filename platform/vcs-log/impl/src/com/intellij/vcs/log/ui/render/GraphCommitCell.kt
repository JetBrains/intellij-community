// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.ui.VcsBookmarkRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Internal
public class GraphCommitCell {

  private final @NotNull String myText;
  private final @NotNull Collection<VcsRef> myRefsToThisCommit;
  private final @NotNull Collection<VcsBookmarkRef> myBookmarksToThisCommit;
  private final @NotNull Collection<? extends PrintElement> myPrintElements;

  private final @Nullable CommitId myCommitId;
  private final boolean myIsLoading;

  public GraphCommitCell(@Nullable CommitId commitId,
                         @NotNull String text,
                         @NotNull Collection<VcsRef> refsToThisCommit,
                         @NotNull Collection<VcsBookmarkRef> bookmarksToThisCommit,
                         @NotNull Collection<? extends PrintElement> printElements,
                         boolean isLoading) {
    myCommitId = commitId;
    myText = text;
    myRefsToThisCommit = refsToThisCommit;
    myBookmarksToThisCommit = bookmarksToThisCommit;
    myPrintElements = printElements;
    myIsLoading = isLoading;
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

  public @Nullable CommitId getCommitId() {
    return myCommitId;
  }

  public boolean isLoading() {
    return myIsLoading;
  }

  @Override
  public String toString() {
    return myText;
  }
}
