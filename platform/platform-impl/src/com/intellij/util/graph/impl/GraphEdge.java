/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.graph.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
class GraphEdge<Node> {
  private final Node myStart;
  private final Node myFinish;
  private final int myDelta;

  GraphEdge(@NotNull Node start, @NotNull Node finish, int delta) {
    myStart = start;
    myFinish = finish;
    myDelta = delta;
  }

  public Node getStart() {
    return myStart;
  }

  public Node getFinish() {
    return myFinish;
  }

  public int getDelta() {
    return myDelta;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GraphEdge edge = (GraphEdge)o;
    return myFinish.equals(edge.myFinish) && myStart.equals(edge.myStart);
  }

  @Override
  public int hashCode() {
    return 31 * myStart.hashCode() + myFinish.hashCode();
  }
}
