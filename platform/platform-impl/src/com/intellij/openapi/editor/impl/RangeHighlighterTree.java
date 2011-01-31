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
      if (o1.data.getLayer() != o2.data.getLayer()) {
        return o2.data.getLayer() - o1.data.getLayer();
      }
      boolean greedyL1 = o1.isGreedyToLeft();
      boolean greedyL2 = o2.isGreedyToLeft();
      if (greedyL1 != greedyL2) return greedyL1 ? -1 : 1;

      int d = o1.intervalEnd() - o1.intervalStart() - (o2.intervalEnd() - o2.intervalStart());
      if (d != 0) return d;

      boolean greedyR1 = o1.isGreedyToRight();
      boolean greedyR2 = o2.isGreedyToRight();
      if (greedyR1 != greedyR2) return greedyR1 ? -1 : 1;

      // for now we tolerate equal range range markers (till lazy creation impl)
      d = (int)(o1.getId() - o2.getId());
      return d;
    }
  };

  public RangeHighlighterTree(Document document) {
    super(document);
  }

  @Override
  protected EqualStartIntervalComparator<IntervalNode> getComparator() {
    return myComparator;
  }

  @Override
  protected RHNode createNewNode(RangeHighlighterEx key, int start, int end, Object data) {
    RHNode node = new RHNode(key, start, end, (RangeHighlighterData)data);
    ((RangeMarkerImpl)key).myNode = node;
    return node;
  }

  class RHNode extends RMNode {
    final RangeHighlighterData data;
    public RHNode(@NotNull final RangeHighlighterEx key, int start, int end, RangeHighlighterData data) {
      super(key, start, end);
      this.data = data;
    }
  }

}
