// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * Use {@link java.util.Set}.
 * </p>
 *
 * @author Eugene Zhuravlev
 */
@Obsolete
public final class Chunk<Node> {
  private final @NotNull Set<Node> myNodes;

  public Chunk(Node node) {
    this(Collections.singleton(node));
  }

  public Chunk(@NotNull Set<Node> nodes) {
    myNodes = nodes;
  }

  public @NotNull Set<Node> getNodes() {
    return myNodes;
  }

  public boolean containsNode(Node node) {
    return myNodes.contains(node);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Chunk)) return false;

    final Chunk chunk = (Chunk)o;

    if (!myNodes.equals(chunk.myNodes)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myNodes.hashCode();
  }

  @Override
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
