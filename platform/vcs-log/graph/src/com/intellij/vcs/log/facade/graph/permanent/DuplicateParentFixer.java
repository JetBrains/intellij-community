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

package com.intellij.vcs.log.facade.graph.permanent;

import com.intellij.vcs.log.GraphCommit;

import java.util.AbstractList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DuplicateParentFixer {

  public static AbstractList<GraphCommit> fixDuplicateParentCommits(final List<? extends GraphCommit> finalCommits) {
    return new AbstractList<GraphCommit>() {
      @Override
      public GraphCommit get(int index) {
        final GraphCommit commit = finalCommits.get(index);
        return new GraphCommit() {
          @Override
          public int getIndex() {
            return commit.getIndex();
          }

          @Override
          public int[] getParentIndices() {
            return fixParentsDuplicate(commit.getParentIndices());
          }
        };
      }

      @Override
      public int size() {
        return finalCommits.size();
      }
    };
  }

  private static int[] fixParentsDuplicate(int[] commitParents) {
    if (commitParents.length == 1)
      return commitParents;

    if (commitParents.length == 2) {
      if (commitParents[0] != commitParents[1])
        return commitParents;
      else
        return new int[]{commitParents[0]};
    }

    Set<Integer> hashes = new HashSet<Integer>();
    for (int hashIndex : commitParents)
      hashes.add(hashIndex);

    if (hashes.size() == commitParents.length)
      return commitParents;

    int[] newCommitParents = new int[hashes.size()];
    int offset = 0;
    for (int hashIndex : commitParents) {
      if (hashes.remove(hashIndex)) {
        newCommitParents[offset] = hashIndex;
        offset++;
      }
    }
    return newCommitParents;
  }
}
