// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.DiffRequestChainBase;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeGoToChangePopupAction;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
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
  public AnAction createGoToChangeAction(@NotNull Consumer<Integer> onSelected) {
    return new ChangeGoToChangePopupAction<ChangeDiffRequestChain>(this, getIndex()) {
      @NotNull
      @Override
      protected DefaultTreeModel buildTreeModel(@NotNull Project project, @NotNull ChangesGroupingPolicyFactory grouping) {
        MultiMap<Object, TreeModelBuilder.GenericNodeData> groups = new MultiMap<>();
        for (int i = 0; i < myProducers.size(); i++) {
          Producer producer = myProducers.get(i);
          FilePath filePath = producer.getFilePath();
          FileStatus fileStatus = producer.getFileStatus();
          Object tag = producer.getPopupTag();
          groups.putValue(tag, new TreeModelBuilder.GenericNodeData(filePath, fileStatus, i));
        }

        TreeModelBuilder builder = new TreeModelBuilder(project, grouping);
        for (Object tag : groups.keySet()) {
          builder.setGenericNodes(groups.get(tag), tag);
        }
        return builder.build();
      }

      @Override
      protected void onSelected(@Nullable Object object) {
        onSelected.consume((Integer)object);
      }
    };

  }

  public interface Producer extends DiffRequestProducer {
    @NotNull
    FilePath getFilePath();

    @NotNull
    FileStatus getFileStatus();

    @Nullable
    default Object getPopupTag() {
      return null;
    }
  }
}
