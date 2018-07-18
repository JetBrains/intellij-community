// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.textCompletion.TextCompletionValueDescriptor;
import com.intellij.util.textCompletion.ValuesCompletionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Delegate part of completion logic to the separate background process,
 * not so slow down typing if it can't be cancelled easily enough.
 */
public abstract class TwoStepCompletionProvider<T> extends ValuesCompletionProvider<T> {
  private static final Logger LOG = Logger.getInstance(TwoStepCompletionProvider.class);

  private static final int TIMEOUT = 100;

  public TwoStepCompletionProvider(@NotNull TextCompletionValueDescriptor<T> presentation) {
    super(presentation, Collections.emptyList());
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                     @NotNull String prefix,
                                     @NotNull CompletionResultSet result) {
    addValues(result, sortVariants(collectSync(result)));

    Future<List<? extends T>> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      return sortVariants(collectAsync(result));
    });

    while (true) {
      try {
        List<? extends T> moreValues = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        if (moreValues != null) {
          addValues(result, moreValues);
          break;
        }
        ProgressManager.checkCanceled();
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
      catch (ProcessCanceledException e) {
        future.cancel(true);
        throw e;
      }
    }
    result.stopHere();
  }

  @NotNull
  private List<? extends T> sortVariants(@NotNull Stream<? extends T> result) {
    return result.sorted(myDescriptor).collect(Collectors.toList());
  }

  private void addValues(@NotNull CompletionResultSet result, @NotNull Collection<? extends T> values) {
    for (T completionVariant : values) {
      result.addElement(installInsertHandler(myDescriptor.createLookupBuilder(completionVariant)));
    }
  }

  @NotNull
  protected abstract Stream<? extends T> collectSync(@NotNull CompletionResultSet result);

  @NotNull
  protected abstract Stream<? extends T> collectAsync(@NotNull CompletionResultSet result);
}
