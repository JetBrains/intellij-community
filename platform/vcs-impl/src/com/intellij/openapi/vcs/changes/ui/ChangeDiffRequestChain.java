// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.AsyncDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChainBase;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vcs.changes.actions.diff.SimpleGoToChangePopupAction;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class ChangeDiffRequestChain extends DiffRequestChainBase implements GoToChangePopupBuilder.Chain {
  private static final Logger LOG = Logger.getInstance(ChangeDiffRequestChain.class);
  @NotNull private final List<? extends Producer> myProducers;

  public ChangeDiffRequestChain(@NotNull List<? extends Producer> producers, int index) {
    super(index);
    if (ContainerUtil.exists(producers, Objects::isNull)) {
      producers = ContainerUtil.skipNulls(producers);
      LOG.error("Producers must not be null");
    }
    myProducers = producers;
  }

  @Override
  @NotNull
  public List<? extends Producer> getRequests() {
    return myProducers;
  }

  @NotNull
  @Override
  public AnAction createGoToChangeAction(@NotNull Consumer<? super Integer> onSelected, int defaultSelection) {
    return createGoToChangeAction(getRequests(), onSelected, defaultSelection);
  }

  @NotNull
  private static AnAction createGoToChangeAction(@NotNull List<? extends Producer> producers,
                                                 @NotNull Consumer<? super Integer> onSelected,
                                                 int defaultSelection) {
    return new SimpleGoToChangePopupAction() {
      @Override
      protected @NotNull ListSelection<? extends PresentableChange> getChanges() {
        return ListSelection.createAt(producers, defaultSelection);
      }

      @Override
      protected void onSelected(@NotNull List<? extends PresentableChange> changes, @Nullable Integer selectedIndex) {
        onSelected.consume(selectedIndex);
      }
    };
  }

  public interface Producer extends DiffRequestProducer, PresentableChange {
  }

  public static abstract class Async extends AsyncDiffRequestChain implements GoToChangePopupBuilder.Chain {
    @NotNull
    @Override
    protected abstract ListSelection<? extends Producer> loadRequestProducers() throws DiffRequestProducerException;

    @Nullable
    @Override
    public AnAction createGoToChangeAction(@NotNull Consumer<? super Integer> onSelected, int defaultSelection) {
      List<? extends DiffRequestProducer> requests = getRequests();

      // may contain other producers with intermediate MessageDiffRequest
      List<Producer> producers = ContainerUtil.map(requests, it -> ObjectUtils.tryCast(it, Producer.class));
      if (!ContainerUtil.all(producers, Conditions.notNull())) return null;

      return ChangeDiffRequestChain.createGoToChangeAction(producers, onSelected, defaultSelection);
    }
  }
}
