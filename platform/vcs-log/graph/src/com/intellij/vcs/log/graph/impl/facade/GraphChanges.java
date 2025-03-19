// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Internal
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
    private final @NotNull NodeId myNodeId;
    private final boolean myRemoved;

    public NodeImpl(@NotNull NodeId nodeId, boolean removed) {
      myNodeId = nodeId;
      myRemoved = removed;
    }

    @Override
    public @NotNull NodeId getNodeId() {
      return myNodeId;
    }

    @Override
    public boolean removed() {
      return myRemoved;
    }
  }

  class EdgeImpl<NodeId> implements Edge<NodeId> {
    private final @Nullable NodeId myUpNodeId;
    private final @Nullable NodeId myDownNodeId;
    private final @Nullable NodeId myTargetId;
    private final boolean myRemoved;

    public EdgeImpl(@Nullable NodeId upNodeId, @Nullable NodeId downNodeId, @Nullable NodeId targetId, boolean removed) {
      myUpNodeId = upNodeId;
      myDownNodeId = downNodeId;
      myTargetId = targetId;
      myRemoved = removed;
    }

    @Override
    public @Nullable NodeId upNodeId() {
      return myUpNodeId;
    }

    @Override
    public @Nullable NodeId downNodeId() {
      return myDownNodeId;
    }

    @Override
    public @Nullable NodeId targetId() {
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

    @Override
    public @NotNull Collection<Node<NodeId>> getChangedNodes() {
      return myChangedNodes;
    }

    @Override
    public @NotNull Collection<Edge<NodeId>> getChangedEdges() {
      return myChangedEdges;
    }
  }
}
