// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.AsyncDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChainBase;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeGoToChangePopupAction;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
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

  /**
   * NB: {@code chain.getRequests()} MUST return instances of {@link Producer}
   */
  @NotNull
  private static ChangeGoToChangePopupAction createGoToChangeAction(@NotNull List<? extends DiffRequestProducer> producers,
                                                                    @NotNull Consumer<? super Integer> onSelected,
                                                                    int defaultSelection) {
    return new ChangeGoToChangePopupAction(producers) {
      @Override
      protected void onSelected(@Nullable ChangesBrowserNode object) {
        GenericChangesBrowserNode node = ObjectUtils.tryCast(object, GenericChangesBrowserNode.class);
        onSelected.consume(node != null ? node.getIndex() : null);
      }

      @Override
      protected Condition<? super DefaultMutableTreeNode> initialSelection() {
        return node -> node instanceof GenericChangesBrowserNode &&
                ((GenericChangesBrowserNode) node).getIndex() == defaultSelection;
      }
    };
  }

  public interface Producer extends DiffRequestProducer {
    @NotNull
    FilePath getFilePath();

    @NotNull
    FileStatus getFileStatus();

    /**
     * @deprecated Use {@link #getTag()} instead.
     */
    @Deprecated
    @Nullable
    default Object getPopupTag() {
      return null;
    }

    @Nullable
    default ChangesBrowserNode.Tag getTag() {
      return ChangesBrowserNode.WrapperTag.wrap(getPopupTag());
    }
  }

  public static abstract class Async extends AsyncDiffRequestChain implements GoToChangePopupBuilder.Chain {
    @NotNull
    @Override
    protected abstract ListSelection<? extends Producer> loadRequestProducers() throws DiffRequestProducerException;

    @Nullable
    @Override
    public AnAction createGoToChangeAction(@NotNull Consumer<? super Integer> onSelected, int defaultSelection) {
      return ChangeDiffRequestChain.createGoToChangeAction(getRequests(), onSelected, defaultSelection);
    }
  }
}
