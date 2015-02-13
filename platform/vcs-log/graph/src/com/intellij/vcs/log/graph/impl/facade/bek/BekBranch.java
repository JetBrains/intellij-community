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

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getDownNodes;

class BekBranch {

  private static final int MAX_BLOCK_SIZE = 20;
  private static final int MAX_DELTA_TIME = 60 * 60 * 24 * 3 * 1000;
  private static final int SMALL_DELTA_TIME = 60 * 60 * 4 * 1000;


  @NotNull private final LinearGraph myPermanentGraph;
  @NotNull private final List<Integer> myNodeIndexes;

  private int myNoInsertSize;

  @Nullable private List<Integer> myPrepareForInsertPart = null;

  public BekBranch(@NotNull LinearGraph permanentGraph, @NotNull List<Integer> nodeIndexes) {
    myPermanentGraph = permanentGraph;
    myNodeIndexes = nodeIndexes;
    myNoInsertSize = myNodeIndexes.size();
  }

  public void updatePrepareForInsertPart(@NotNull TimestampGetter timestampGetter, @NotNull BekEdgeRestrictions edgeRestrictions) {
    assert myPrepareForInsertPart == null;
    int currentNode = myNodeIndexes.get(myNoInsertSize - 1);

    if (edgeRestrictions.hasRestriction(currentNode)) return;

    int prevIndex;
    for (prevIndex = myNoInsertSize - 1; prevIndex > 0; prevIndex--) {
      int upNode = myNodeIndexes.get(prevIndex - 1);
      int downNode = myNodeIndexes.get(prevIndex);

      // for correct topological order
      if (edgeRestrictions.hasRestriction(upNode)) break;

      // upNode is mergeCommit
      List<Integer> downNodes = getDownNodes(myPermanentGraph, upNode);
      if (downNodes.size() > 1 && downNodes.contains(downNode)) continue;

      // division
      if (!downNodes.contains(downNode)) break;

      long delta = Math.abs(timestampGetter.getTimestamp(upNode) - timestampGetter.getTimestamp(downNode));

      // long time between commits
      if (delta > MAX_DELTA_TIME) break;

      // if block so long
      if (prevIndex < myNoInsertSize - MAX_BLOCK_SIZE && delta > SMALL_DELTA_TIME) break;
    }

    myPrepareForInsertPart = myNodeIndexes.subList(prevIndex, myNoInsertSize);
  }

  @Nullable
  public List<Integer> getPrepareForInsertPart() {
    return myPrepareForInsertPart;
  }

  public void doneInsertPreparedPart() {
    assert myPrepareForInsertPart != null;
    myNoInsertSize -= myPrepareForInsertPart.size();
    myPrepareForInsertPart = null;
  }

  public boolean isDone() {
    return myNoInsertSize == 0;
  }
}
