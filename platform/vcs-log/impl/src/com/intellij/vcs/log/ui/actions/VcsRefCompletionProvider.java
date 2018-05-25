// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.textCompletion.TextCompletionValueDescriptor;
import com.intellij.util.textCompletion.ValuesCompletionProvider;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VcsRefCompletionProvider extends ValuesCompletionProvider<VcsRef> {
  private static final Logger LOG = Logger.getInstance(VcsRefCompletionProvider.class);

  private static final int TIMEOUT = 100;
  @NotNull private final VcsLogRefs myRefs;
  @NotNull private final Set<VirtualFile> myRoots;

  public VcsRefCompletionProvider(@NotNull VcsLogRefs refs,
                                  @NotNull Collection<VirtualFile> roots,
                                  @NotNull TextCompletionValueDescriptor<VcsRef> descriptor) {
    super(descriptor, ContainerUtil.emptyList());
    myRefs = refs;
    myRoots = new HashSet<>(roots);
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                     @NotNull String prefix,
                                     @NotNull CompletionResultSet result) {
    addValues(result, filterAndSort(result, myRefs.getBranches().stream()));

    Future<List<VcsRef>> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      return filterAndSort(result, myRefs.stream().filter(ref -> !ref.getType().isBranch()));
    });

    while (true) {
      try {
        List<VcsRef> tags = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        if (tags != null) {
          addValues(result, tags);
          break;
        }
      }
      catch (InterruptedException | CancellationException e) {
        break;
      }
      catch (TimeoutException ignored) {
      }
      catch (ExecutionException e) {
        LOG.error(e);
        break;
      }
      ProgressManager.checkCanceled();
    }
    result.stopHere();
  }

  private void addValues(@NotNull CompletionResultSet result, @NotNull Collection<? extends VcsRef> values) {
    for (VcsRef completionVariant : values) {
      result.addElement(installInsertHandler(myDescriptor.createLookupBuilder(completionVariant)));
    }
  }

  @NotNull
  private List<VcsRef> filterAndSort(@NotNull CompletionResultSet result, @NotNull Stream<VcsRef> stream) {
    Stream<VcsRef> matched = stream.filter(ref -> {
      return myRoots.contains(ref.getRoot()) && result.getPrefixMatcher().prefixMatches(ref.getName());
    });
    return filterRefs(matched).sorted(myDescriptor).collect(Collectors.toList());
  }

  @NotNull
  protected Stream<VcsRef> filterRefs(@NotNull Stream<VcsRef> vcsRefs) {
    return vcsRefs;
  }
}
