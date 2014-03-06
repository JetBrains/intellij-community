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

package com.intellij.vcs.log.newgraph;

import com.intellij.vcs.log.newgraph.utils.Flags;

public class GraphFlags {
  private final int myNodesCount;
  private final byte[] myFlags;

  public GraphFlags(int nodesCount) {
    myNodesCount = nodesCount;
    myFlags = new byte[nodesCount];
  }

  private Flags getFlags(int offset) {
    assert offset >= 0 && offset < 8;
    final int mask = 1 << offset;
    return new Flags() {
      @Override
      public int size() {
        return myNodesCount;
      }

      @Override
      public boolean get(int index) {
        return (myFlags[index] & mask) != 0;
      }

      @Override
      public void set(int index, boolean value) {
        if (value) {
          myFlags[index] |= mask;
        } else {
          myFlags[index] &= ~mask;
        }
      }
    };
  }

  public Flags getVisibleNodes() {
    return getFlags(1);
  }

  public Flags getVisibleNodesInBranches() {
    return getFlags(3);
  }

  // From node k exist only one edge to k + 1
  public Flags getSimpleNodeFlags() {
    return getFlags(0);
  }

  public Flags getThickFlags() {
    return getFlags(2);
  }

  public Flags getFlagsForFilters() {
    return getFlags(4);
  }

  public Flags getTempFlags() {
    return getFlags(5);
  }
}
