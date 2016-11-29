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
package com.intellij.vcs.log.graph.impl.facade.bek;

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

class BekEdgeRestrictions {
  @NotNull private final MultiMap<Integer, Integer> myUpToEdge = new MultiMap<>();

  @NotNull private final MultiMap<Integer, Integer> myDownToEdge = new MultiMap<>();

  void addRestriction(int upNode, int downNode) {
    myUpToEdge.putValue(upNode, downNode);
    myDownToEdge.putValue(downNode, upNode);
  }

  void removeRestriction(int downNode) {
    for (int upNode : myDownToEdge.get(downNode)) {
      myUpToEdge.remove(upNode, downNode);
    }
  }

  boolean hasRestriction(int upNode) {
    return myUpToEdge.containsKey(upNode);
  }
}
