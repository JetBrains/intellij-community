// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class BekBranch {
  private static final Logger LOG = Logger.getInstance(BekBranch.class);

  private static final int MAX_BLOCK_SIZE = 20;
  private static final int MAX_DELTA_TIME = 60 * 60 * 24 * 3 * 1000;
  private static final int SMALL_DELTA_TIME = 60 * 60 * 4 * 1000;


  private final @NotNull LinearGraph myPermanentGraph;
  private final @NotNull IntList myNodeIndexes;
  private final @NotNull IntList myDownNodesBuffer = new IntArrayList();

  private int myNoInsertSize;

  private @Nullable IntList myPrepareForInsertPart = null;

  BekBranch(@NotNull LinearGraph permanentGraph, @NotNull IntList nodeIndexes) {
    myPermanentGraph = permanentGraph;
    myNodeIndexes = nodeIndexes;
    myNoInsertSize = myNodeIndexes.size();
  }

  public @NotNull IntList getNodeIndexes() {
    return myNodeIndexes;
  }

  /**
   * @return the lowest node of the branch that is not placed yet
   */
  public int getCurrentNode() {
    LOG.assertTrue(!isDone());
    return myNodeIndexes.getInt(myNoInsertSize - 1);
  }

  public void updatePrepareForInsertPart(@NotNull TimestampGetter timestampGetter, @NotNull BekEdgeRestrictions edgeRestrictions) {
    LOG.assertTrue(myPrepareForInsertPart == null);
    int currentNode = myNodeIndexes.getInt(myNoInsertSize - 1);

    if (edgeRestrictions.hasRestriction(currentNode)) return;

    int prevIndex;
    for (prevIndex = myNoInsertSize - 1; prevIndex > 0; prevIndex--) {
      int upNode = myNodeIndexes.getInt(prevIndex - 1);
      int downNode = myNodeIndexes.getInt(prevIndex);

      // for correct topological order
      if (edgeRestrictions.hasRestriction(upNode)) break;

      LinearGraphUtils.collectDownNodes(myPermanentGraph, upNode, myDownNodesBuffer);
      boolean containsDownNode = myDownNodesBuffer.contains(downNode);

      // upNode is mergeCommit
      if (myDownNodesBuffer.size() > 1 && containsDownNode) continue;

      // division
      if (!containsDownNode) break;

      long delta = Math.abs(timestampGetter.getTimestamp(upNode) - timestampGetter.getTimestamp(downNode));

      // long time between commits
      if (delta > MAX_DELTA_TIME) break;

      // if block so long
      if (prevIndex < myNoInsertSize - MAX_BLOCK_SIZE && delta > SMALL_DELTA_TIME) break;
    }

    myPrepareForInsertPart = myNodeIndexes.subList(prevIndex, myNoInsertSize);
  }

  public @Nullable IntList getPrepareForInsertPart() {
    return myPrepareForInsertPart;
  }

  public void doneInsertPreparedPart() {
    LOG.assertTrue(myPrepareForInsertPart != null);
    myNoInsertSize -= myPrepareForInsertPart.size();
    myPrepareForInsertPart = null;
  }

  public boolean isDone() {
    return myNoInsertSize == 0;
  }
}
