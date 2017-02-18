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
package com.intellij.vcs.log.graph.collapsing;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

class TreeSetNodeIterator {
  private final SortedSet<Integer> myWalkNodes;

  TreeSetNodeIterator(int startNode, final boolean isUp) {
    myWalkNodes = new TreeSet<>(new Comparator<Integer>() {
      @Override
      public int compare(@NotNull Integer o1, @NotNull Integer o2) {
        if (isUp) return o2 - o1;
        return o1 - o2;
      }
    });
    myWalkNodes.add(startNode);
  }

  public Integer pop() {
    Integer next = myWalkNodes.first();
    myWalkNodes.remove(next);
    return next;
  }

  public boolean notEmpty() {
    return !myWalkNodes.isEmpty();
  }

  public void addAll(List<Integer> nodes) {
    myWalkNodes.addAll(nodes);
  }
}
