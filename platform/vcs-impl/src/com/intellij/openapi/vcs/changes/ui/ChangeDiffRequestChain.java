// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.AsyncDiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.chains.DiffRequestSelectionChain;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vcs.changes.actions.diff.PresentableGoToChangePopupAction;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Supports typical tree-like "Go to Change" navigation popup.
 *
 * @see ChangeDiffRequestChain.Async
 */
public class ChangeDiffRequestChain extends UserDataHolderBase implements DiffRequestSelectionChain, GoToChangePopupBuilder.Chain {
  private final @NotNull ListSelection<? extends Producer> myProducers;

  public ChangeDiffRequestChain(@NotNull ListSelection<? extends Producer> producers) {
    myProducers = producers;
  }

  public ChangeDiffRequestChain(@NotNull List<? extends Producer> producers, int index) {
    this(ListSelection.createAt(producers, index));
  }

  @Override
  public @NotNull ListSelection<? extends Producer> getListSelection() {
    return myProducers;
  }

  @Override
  public @NotNull List<? extends Producer> getRequests() {
    return myProducers.getList();
  }

  @Override
  public @NotNull AnAction createGoToChangeAction(@NotNull Consumer<? super Integer> onSelected, int defaultSelection) {
    return createGoToChangeAction(getRequests(), onSelected, defaultSelection);
  }

  private static @NotNull AnAction createGoToChangeAction(@NotNull List<? extends Producer> producers,
                                                          @NotNull Consumer<? super Integer> onSelected,
                                                          int defaultSelection) {
    return new PresentableGoToChangePopupAction<ProducerWrapper>() {
      @Override
      protected @NotNull ListSelection<? extends ProducerWrapper> getChanges() {
        List<ProducerWrapper> wrappers = new ArrayList<>();
        for (int i = 0; i < producers.size(); i++) {
          wrappers.add(new ProducerWrapper(producers.get(i), i));
        }
        return ListSelection.createAt(wrappers, defaultSelection);
      }

      @Override
      protected PresentableChange getPresentation(@NotNull ProducerWrapper change) {
        return change.producer;
      }

      @Override
      protected void onSelected(@NotNull ProducerWrapper change) {
        onSelected.consume(change.index);
      }
    };
  }

  private static class ProducerWrapper {
    public final @NotNull Producer producer;
    public final int index;

    private ProducerWrapper(@NotNull Producer producer, int index) {
      this.producer = producer;
      this.index = index;
    }
  }

  public interface Producer extends DiffRequestProducer, PresentableChange {
    @Override
    default @NotNull FileType getContentType() {
      return getFilePath().getFileType();
    }
  }

  public abstract static class Async extends AsyncDiffRequestChain implements GoToChangePopupBuilder.Chain {
    @Override
    protected abstract @NotNull ListSelection<? extends Producer> loadRequestProducers() throws DiffRequestProducerException;

    @Override
    public @Nullable AnAction createGoToChangeAction(@NotNull Consumer<? super Integer> onSelected, int defaultSelection) {
      List<? extends DiffRequestProducer> requests = getRequests();

      // may contain other producers with intermediate MessageDiffRequest
      List<Producer> producers = ContainerUtil.map(requests, it -> ObjectUtils.tryCast(it, Producer.class));
      if (!ContainerUtil.all(producers, Conditions.notNull())) return null;

      return ChangeDiffRequestChain.createGoToChangeAction(producers, onSelected, defaultSelection);
    }
  }
}
