// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.textCompletion.TextCompletionValueDescriptor;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class VcsRefCompletionProvider extends TwoStepCompletionProvider<VcsRef> {
  private final @NotNull VcsLogRefs myRefs;
  private final @NotNull Set<VirtualFile> myRoots;

  public VcsRefCompletionProvider(@NotNull VcsLogRefs refs,
                                  @NotNull Collection<? extends VirtualFile> roots,
                                  @NotNull TextCompletionValueDescriptor<VcsRef> descriptor) {
    super(descriptor);
    myRefs = refs;
    myRoots = new HashSet<>(roots);
  }

  @Override
  protected @NotNull Stream<? extends VcsRef> collectSync(@NotNull CompletionResultSet result) {
    return filterAndSort(result, myRefs.getBranches().stream());
  }

  @Override
  protected @NotNull Stream<? extends VcsRef> collectAsync(@NotNull CompletionResultSet result) {
    return filterAndSort(result, myRefs.stream().filter(ref -> !ref.getType().isBranch()));
  }

  private @NotNull Stream<VcsRef> filterAndSort(@NotNull CompletionResultSet result, @NotNull Stream<VcsRef> stream) {
    Stream<VcsRef> matched = stream.filter(ref -> myRoots.contains(ref.getRoot()) &&
                                                  result.getPrefixMatcher().prefixMatches(ref.getName()));
    return filterRefs(matched);
  }

  protected @NotNull Stream<VcsRef> filterRefs(@NotNull Stream<VcsRef> vcsRefs) {
    return vcsRefs;
  }
}
