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
package com.intellij.vcs.log.newgraph.gpaph.impl;

import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NodeImpl implements Node {
  private final int myVisibleNodeIndex;

  @NotNull
  private final Type myType;

  @NotNull
  private final List<Edge> myUpEdges;

  @NotNull
  private final List<Edge> myDownEdges;
  private final int myLayoutIndex;

  public NodeImpl(int visibleNodeIndex, @NotNull Type type, @NotNull List<Edge> upEdges, @NotNull List<Edge> downEdges, int layoutIndex) {
    myVisibleNodeIndex = visibleNodeIndex;
    myType = type;
    myUpEdges = upEdges;
    myDownEdges = downEdges;
    myLayoutIndex = layoutIndex;
  }


  @Override
  public int getVisibleNodeIndex() {
    return myVisibleNodeIndex;
  }

  @NotNull
  @Override
  public Type getType() {
    return myType;
  }

  @NotNull
  @Override
  public List<Edge> getUpEdges() {
    return myUpEdges;
  }

  @NotNull
  @Override
  public List<Edge> getDownEdges() {
    return myDownEdges;
  }

  @Override
  public int getLayoutIndex() {
    return myLayoutIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NodeImpl)) return false;

    NodeImpl node = (NodeImpl)o;

    if (myLayoutIndex != node.myLayoutIndex) return false;
    if (myVisibleNodeIndex != node.myVisibleNodeIndex) return false;
    if (myType != node.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myVisibleNodeIndex;
    result = 31 * result + myType.hashCode();
    result = 31 * result + myLayoutIndex;
    return result;
  }
}
