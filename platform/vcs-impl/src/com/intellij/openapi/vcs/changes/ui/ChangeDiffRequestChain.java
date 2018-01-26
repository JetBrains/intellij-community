/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      protected DefaultTreeModel buildTreeModel(@NotNull Project project, boolean showFlatten) {
        MultiMap<Object, TreeModelBuilder.GenericNodeData> groups = new MultiMap<>();
        for (int i = 0; i < myProducers.size(); i++) {
          Producer producer = myProducers.get(i);
          FilePath filePath = producer.getFilePath();
          FileStatus fileStatus = producer.getFileStatus();
          Object tag = producer.getPopupTag();
          groups.putValue(tag, new TreeModelBuilder.GenericNodeData(filePath, fileStatus, i));
        }

        TreeModelBuilder builder = new TreeModelBuilder(project, showFlatten);
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
