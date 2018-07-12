// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull private final VcsLogRefs myRefs;
  @NotNull private final Set<VirtualFile> myRoots;

  public VcsRefCompletionProvider(@NotNull VcsLogRefs refs,
                                  @NotNull Collection<VirtualFile> roots,
                                  @NotNull TextCompletionValueDescriptor<VcsRef> descriptor) {
    super(descriptor);
    myRefs = refs;
    myRoots = new HashSet<>(roots);
  }

  @NotNull
  @Override
  protected Stream<? extends VcsRef> collectSync(@NotNull CompletionResultSet result) {
    return filterAndSort(result, myRefs.getBranches().stream());
  }

  @NotNull
  @Override
  protected Stream<? extends VcsRef> collectAsync(@NotNull CompletionResultSet result) {
    return filterAndSort(result, myRefs.stream().filter(ref -> !ref.getType().isBranch()));
  }

  @NotNull
  private Stream<VcsRef> filterAndSort(@NotNull CompletionResultSet result, @NotNull Stream<VcsRef> stream) {
    Stream<VcsRef> matched = stream.filter(ref -> {
      return myRoots.contains(ref.getRoot()) && result.getPrefixMatcher().prefixMatches(ref.getName());
    });
    return filterRefs(matched);
  }

  @NotNull
  protected Stream<VcsRef> filterRefs(@NotNull Stream<VcsRef> vcsRefs) {
    return vcsRefs;
  }
}
