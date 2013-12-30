/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NodeAroundProvider implements AroundProvider<Node> {

  @NotNull private final DataPack myDataPack;
  @NotNull private final VcsLogDataHolder myDataHolder;

  public NodeAroundProvider(@NotNull DataPack pack, @NotNull VcsLogDataHolder dataHolder) {
    myDataPack = pack;
    myDataHolder = dataHolder;
  }

  @NotNull
  @Override
  public MultiMap<VirtualFile, Hash> getCommitsAround(@NotNull Node node, int above, int below) {
    MultiMap<VirtualFile, Hash> commits = MultiMap.create();
    int rowIndex = node.getRowIndex();
    for (int i = rowIndex - above; i < rowIndex + below; i++) {
      Node commitNode = getCommitNodeInRow(i);
      if (commitNode != null) {
        Hash hash = myDataHolder.getHash(commitNode.getCommitIndex());
        commits.putValue(commitNode.getBranch().getRepositoryRoot(), hash);
      }
    }
    return commits;
  }

  @NotNull
  @Override
  public Hash resolveId(@NotNull Node node) {
    return myDataHolder.getHash(node.getCommitIndex());
  }

  @Nullable
  private Node getCommitNodeInRow(int rowIndex) {
    Graph graph = myDataPack.getGraphModel().getGraph();
    if (rowIndex < 0 || rowIndex >= graph.getNodeRows().size()) {
      return null;
    }
    NodeRow row = graph.getNodeRows().get(rowIndex);
    for (Node node : row.getNodes()) {
      if (node.getType() == Node.NodeType.COMMIT_NODE || node.getType() == Node.NodeType.END_COMMIT_NODE) {
        return node;
      }
    }
    return null;
  }

}
