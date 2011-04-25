/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
public class RangeHighlighterTree extends RangeMarkerTree<RangeHighlighterEx> {
  private final EqualStartIntervalComparator<IntervalNode> myComparator = new EqualStartIntervalComparator<IntervalNode>() {
    @Override
    public int compare(IntervalNode i1, IntervalNode i2) {
      RHNode o1 = (RHNode)i1;
      RHNode o2 = (RHNode)i2;
      if (o1.myLayer != o2.myLayer) {
        return o2.myLayer - o1.myLayer;
      }
      boolean greedyL1 = o1.isGreedyToLeft();
      boolean greedyL2 = o2.isGreedyToLeft();
      if (greedyL1 != greedyL2) return greedyL1 ? -1 : 1;

      int d = o1.intervalEnd() - o1.intervalStart() - (o2.intervalEnd() - o2.intervalStart());
      if (d != 0) return d;

      boolean greedyR1 = o1.isGreedyToRight();
      boolean greedyR2 = o2.isGreedyToRight();
      if (greedyR1 != greedyR2) return greedyR1 ? -1 : 1;

      return 0;
    }
  };

  public RangeHighlighterTree(Document document) {
    super(document);
  }

  @Override
  protected EqualStartIntervalComparator<IntervalNode> getComparator() {
    return myComparator;
  }

  @NotNull
  @Override
  protected RHNode createNewNode(@NotNull RangeHighlighterEx key, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    return new RHNode(key, start, end, greedyToLeft, greedyToRight,layer);
  }

  class RHNode extends RangeMarkerTree<RangeHighlighterEx>.RMNode {
    final int myLayer;

    public RHNode(@NotNull final RangeHighlighterEx key,
                  int start,
                  int end,
                  boolean greedyToLeft,
                  boolean greedyToRight,
                  int layer) {
      super(key, start, end, greedyToLeft, greedyToRight);
      myLayer = layer;
    }

    //range highlighters are strongly referenced
    @Override
    protected Getable<RangeHighlighterEx> createGetable(@NotNull RangeHighlighterEx interval) {
      return (Getable)interval;
    }
  }
}
