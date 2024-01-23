// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import org.jetbrains.plugins.gradle.model.ExternalDependency;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

/**
 * @author Vladislav.Soroka
 */
public class DependencyTraverser implements Iterable<ExternalDependency> {
  private final Collection<ExternalDependency> collection;

  public DependencyTraverser(Collection<ExternalDependency> c) {
    collection = c;
  }

  @Override
  public Iterator<ExternalDependency> iterator() {
    return new Itr(collection);
  }

  private static class Itr implements Iterator<ExternalDependency> {
    Queue<ExternalDependency> queue;

    Itr(Collection<ExternalDependency> c) {
      queue = new ArrayDeque<>(c);
    }

    @Override
    public boolean hasNext() {
      return !queue.isEmpty();
    }

    @Override
    public ExternalDependency next() {
      ExternalDependency dependency = queue.remove();
      queue.addAll(dependency.getDependencies());
      return dependency;
    }
  }
}