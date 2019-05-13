/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.impl.facade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface GraphChanges<NodeId> {

  @NotNull
  Collection<Node<NodeId>> getChangedNodes();

  @NotNull
  Collection<Edge<NodeId>> getChangedEdges();

  interface Node<NodeId> {
    @NotNull
    NodeId getNodeId();

    boolean removed();
  }

  interface Edge<NodeId> {
    @Nullable
    NodeId upNodeId();

    @Nullable
    NodeId downNodeId();

    @Nullable
    NodeId targetId();

    boolean removed();
  }

  class NodeImpl<NodeId> implements Node<NodeId> {
    @NotNull private final NodeId myNodeId;
    private final boolean myRemoved;

    public NodeImpl(@NotNull NodeId nodeId, boolean removed) {
      myNodeId = nodeId;
      myRemoved = removed;
    }

    @NotNull
    @Override
    public NodeId getNodeId() {
      return myNodeId;
    }

    @Override
    public boolean removed() {
      return myRemoved;
    }
  }

  class EdgeImpl<NodeId> implements Edge<NodeId> {
    @Nullable private final NodeId myUpNodeId;
    @Nullable private final NodeId myDownNodeId;
    @Nullable private final NodeId myTargetId;
    private final boolean myRemoved;

    public EdgeImpl(@Nullable NodeId upNodeId, @Nullable NodeId downNodeId, @Nullable NodeId targetId, boolean removed) {
      myUpNodeId = upNodeId;
      myDownNodeId = downNodeId;
      myTargetId = targetId;
      myRemoved = removed;
    }

    @Nullable
    @Override
    public NodeId upNodeId() {
      return myUpNodeId;
    }

    @Nullable
    @Override
    public NodeId downNodeId() {
      return myDownNodeId;
    }

    @Nullable
    @Override
    public NodeId targetId() {
      return myTargetId;
    }

    @Override
    public boolean removed() {
      return myRemoved;
    }
  }

  class GraphChangesImpl<NodeId> implements GraphChanges<NodeId> {
    private final Collection<Node<NodeId>> myChangedNodes;
    private final Collection<Edge<NodeId>> myChangedEdges;

    public GraphChangesImpl(Collection<Node<NodeId>> changedNodes, Collection<Edge<NodeId>> changedEdges) {
      myChangedNodes = changedNodes;
      myChangedEdges = changedEdges;
    }

    @NotNull
    @Override
    public Collection<Node<NodeId>> getChangedNodes() {
      return myChangedNodes;
    }

    @NotNull
    @Override
    public Collection<Edge<NodeId>> getChangedEdges() {
      return myChangedEdges;
    }
  }
}
