// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.*;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeGoToChangePopupAction;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.sorted;
import static java.util.Comparator.comparing;

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
    return createGoToChangeAction(this, onSelected, defaultSelection);
  }

  /**
   * NB: {@code chain.getRequests()} MUST return instances of {@link Producer}
   */
  @NotNull
  private static ChangeGoToChangePopupAction<DiffRequestChain> createGoToChangeAction(@NotNull DiffRequestChain chain,
                                                                                      @NotNull Consumer<? super Integer> onSelected,
                                                                                      int defaultSelection) {
    return new ChangeGoToChangePopupAction<>(chain) {
      @NotNull
      @Override
      protected DefaultTreeModel buildTreeModel(@NotNull Project project, @NotNull ChangesGroupingPolicyFactory grouping) {
        MultiMap<ChangesBrowserNode.Tag, GenericChangesBrowserNode> groups = new MultiMap<>();
        List<? extends DiffRequestProducer> producers = chain.getRequests();
        for (int i = 0; i < producers.size(); i++) {
          Producer producer = ObjectUtils.tryCast(producers.get(i), Producer.class);
          if (producer == null) continue;

          FilePath filePath = producer.getFilePath();
          FileStatus fileStatus = producer.getFileStatus();
          ChangesBrowserNode.Tag tag = producer.getTag();
          groups.putValue(tag, new GenericChangesBrowserNode(filePath, fileStatus, i));
        }

        MyTreeModelBuilder builder = new MyTreeModelBuilder(project, grouping);
        for (ChangesBrowserNode.Tag tag : groups.keySet()) {
          builder.setGenericNodes(groups.get(tag), tag);
        }
        return builder.build();
      }

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

  private static class MyTreeModelBuilder extends TreeModelBuilder {
    MyTreeModelBuilder(@NotNull Project project, @NotNull ChangesGroupingPolicyFactory grouping) {
      super(project, grouping);
    }

    public void setGenericNodes(@NotNull Collection<GenericChangesBrowserNode> nodes, @Nullable ChangesBrowserNode.Tag tag) {
      ChangesBrowserNode<?> parentNode = createTagNode(tag);

      for (GenericChangesBrowserNode node : sorted(nodes, comparing(data -> data.myFilePath, PATH_COMPARATOR))) {
        insertChangeNode(node.myFilePath, parentNode, node);
      }
    }
  }

  private static class GenericChangesBrowserNode extends ChangesBrowserNode<FilePath> implements Comparable<GenericChangesBrowserNode> {
    @NotNull private final FilePath myFilePath;
    @NotNull private final FileStatus myFileStatus;
    private final int myIndex;

    protected GenericChangesBrowserNode(@NotNull FilePath filePath, @NotNull FileStatus fileStatus, int index) {
      super(filePath);
      myFilePath = filePath;
      myFileStatus = fileStatus;
      myIndex = index;
    }

    private int getIndex() {
      return myIndex;
    }

    @Override
    protected boolean isFile() {
      return !isDirectory();
    }

    @Override
    protected boolean isDirectory() {
      return myFilePath.isDirectory();
    }

    @Override
    public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
      renderer.appendFileName(myFilePath.getVirtualFile(), myFilePath.getName(), myFileStatus.getColor());

      if (renderer.isShowFlatten()) {
        appendParentPath(renderer, myFilePath.getParentPath());
      }

      if (!renderer.isShowFlatten() && getFileCount() != 1 || getDirectoryCount() != 0) {
        appendCount(renderer);
      }

      renderer.setIcon(myFilePath, myFilePath.isDirectory() || !isLeaf());
    }

    @Override
    public String getTextPresentation() {
      return myFilePath.getName();
    }

    @Override
    public String toString() {
      return FileUtil.toSystemDependentName(myFilePath.getPath());
    }

    @Override
    public int compareTo(@NotNull GenericChangesBrowserNode o) {
      return compareFilePaths(myFilePath, o.myFilePath);
    }
  }


  public static abstract class Async extends AsyncDiffRequestChain implements GoToChangePopupBuilder.Chain {
    @NotNull
    @Override
    protected abstract ListSelection<? extends Producer> loadRequestProducers() throws DiffRequestProducerException;

    @Nullable
    @Override
    public AnAction createGoToChangeAction(@NotNull Consumer<? super Integer> onSelected, int defaultSelection) {
      return ChangeDiffRequestChain.createGoToChangeAction(this, onSelected, defaultSelection);
    }
  }
}
