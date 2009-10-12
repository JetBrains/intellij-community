/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 27, 2004
 */
public class Chunk<Node> {
  private final Set<Node> myNodes;

  public Chunk(Node node) {
    this(new LinkedHashSet<Node>());
    myNodes.add(node);
  }
  
  public Chunk(Set<Node> nodes) {
    myNodes = nodes;
  }

  public Set<Node> getNodes() {
    return myNodes;
  }

  public boolean containsNode(Node node) {
    return myNodes.contains(node);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Chunk)) return false;

    final Chunk chunk = (Chunk)o;

    if (!myNodes.equals(chunk.myNodes)) return false;

    return true;
  }

  public int hashCode() {
    return myNodes.hashCode();
  }

  public String toString() { // for debugging only
    final StringBuilder buf = new StringBuilder();
    buf.append("[");
    for (final Node node : myNodes) {
      if (buf.length() > 1) {
        buf.append(", ");
      }
      buf.append(node.toString());
    }
    buf.append("]");
    return buf.toString();
  }
}
