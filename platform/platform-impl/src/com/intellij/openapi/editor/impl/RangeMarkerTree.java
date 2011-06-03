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
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Segment;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: cdr
 */
public class RangeMarkerTree<T extends RangeMarkerEx> extends IntervalTreeImpl<T> {
  private final PrioritizedDocumentListener myListener;
  final Document myDocument;
  private final EqualStartIntervalComparator<IntervalNode> myEqualStartIntervalComparator = new EqualStartIntervalComparator<IntervalNode>() {
    @Override
    public int compare(IntervalNode i1, IntervalNode i2) {
      RMNode o1 = (RMNode)i1;
      RMNode o2 = (RMNode)i2;
      boolean greedyL1 = o1.isGreedyToLeft();
      boolean greedyL2 = o2.isGreedyToLeft();
      if (greedyL1 != greedyL2) return greedyL1 ? -1 : 1;

      int o1Length = o1.intervalEnd() - o1.intervalStart();
      int o2Length = o2.intervalEnd() - o2.intervalStart();
      int d = o1Length - o2Length;
      if (d != 0) return d;

      boolean greedyR1 = o1.isGreedyToRight();
      boolean greedyR2 = o2.isGreedyToRight();
      if (greedyR1 != greedyR2) return greedyR1 ? -1 : 1;

      return 0;
    }
  };

  @Override
  protected EqualStartIntervalComparator<IntervalNode> getComparator() {
    return myEqualStartIntervalComparator;
  }


  protected RangeMarkerTree(Document document) {
    myDocument = document;
    myListener = new PrioritizedDocumentListener() {
      public int getPriority() {
        return EditorDocumentPriorities.RANGE_MARKER; // Need to make sure we invalidate all the stuff before someone (like LineStatusTracker) starts to modify highlights.
      }

      public void beforeDocumentChange(DocumentEvent event) {}

      public void documentChanged(DocumentEvent e) {
        updateMarkersOnChange(e);
      }
    };

    document.addDocumentListener(myListener);
  }

  public void dispose() {
    myDocument.removeDocumentListener(myListener);
  }

  @Override
  public RangeMarkerTree<T>.RMNode addInterval(@NotNull T interval, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    RangeMarkerImpl marker = (RangeMarkerImpl)interval;
    marker.setValid(true);
    RangeMarkerTree<T>.RMNode node = (RMNode)super.addInterval(interval, start, end, greedyToLeft, greedyToRight, layer);

    checkBelongsToTheTree(interval, true);

    return node;
  }

  @NotNull
  @Override
  protected RMNode createNewNode(@NotNull T key, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    return new RMNode(key, start, end, greedyToLeft, greedyToRight);
  }

  @Override
  protected void checkBelongsToTheTree(T interval, boolean assertInvalid) {
    assert ((RangeMarkerImpl)interval).myDocument == myDocument;
    super.checkBelongsToTheTree(interval, assertInvalid);
  }

  @Override
  protected RangeMarkerTree<T>.RMNode lookupNode(@NotNull T key) {
    return (RMNode)((RangeMarkerImpl)key).myNode;
  }

  @Override
  protected void setNode(@NotNull T key, IntervalNode intervalNode) {
    ((RangeMarkerImpl)key).myNode = (RMNode)intervalNode;
  }

  public class RMNode extends IntervalTreeImpl<T>.IntervalNode {
    private final boolean isExpandToLeft;
    private final boolean isExpandToRight;

    public RMNode(@NotNull T key, int start, int end, boolean greedyToLeft, boolean greedyToRight) {
      super(key, start, end);
      isExpandToLeft = greedyToLeft;
      isExpandToRight = greedyToRight;
    }

    public boolean isGreedyToLeft() {
      return isExpandToLeft;
    }

    public boolean isGreedyToRight() {
      return isExpandToRight;
    }

    //@Override
    //public void addInterval(@NotNull T interval) {
    //  super.addInterval(interval);
    //  ((RangeMarkerImpl)interval).myNode = this;
    //  checkBelongsToTheTree(interval, true);
    //}

    @Override
    public String toString() {
      return (isGreedyToLeft() ? "[" : "(") + intervalStart() + "," + intervalEnd() + (isGreedyToRight() ? "]" : ")");
    }
  }

  private void updateMarkersOnChange(DocumentEvent e) {
    long start = System.currentTimeMillis();
    try {
      l.writeLock().lock();
      if (size() == 0) return;
      checkMax(true);

      modCount++;
      List<IntervalNode> affected = new ArrayList<IntervalNode>();
      normalized &= collectAffectedMarkers(getRoot(), e, affected);
      checkMax(false);

      if (!affected.isEmpty()) {
        for (IntervalNode node : affected) {
          // assumption: interval.getEndOffset() will never be accessed during remove()
          int startOffset = node.intervalStart();
          int endOffset = node.intervalEnd();
          removeNode(node);
          node.delta = 0;   // we can do it because all the deltas in the way were cleared in the collectAffectedMarkers
          node.setParent(null);
          node.setLeft(null);
          node.setRight(null);
          node.setValid(true);
          assert node.intervalStart() == startOffset;
          assert node.intervalEnd() == endOffset;
        }
        checkMax(true);
        for (IntervalNode node : affected) {
          List<Getable<T>> keys = node.intervals;
          if (keys.isEmpty()) continue; // collected away

          RangeMarkerImpl marker = null;
          for (int i = keys.size() - 1; i >= 0; i--) {
            Getable<T> key = keys.get(i);
            marker = (RangeMarkerImpl)key.get();
            if (marker != null) {
              if (!marker.isValid()) {
                // marker can become invalid on its own, e.g. FoldRegion
                node.removeIntervalInternal(i);
                continue;
              }
              break;
            }
          }
          if (marker == null) continue; // node remains removed from the tree
          marker.documentChanged(e);
          if (marker.isValid()) {
            RMNode insertedNode = (RMNode)findOrInsert(node);
            // can change if two range become the one
            if (insertedNode != node) {
              // merge happened
              for (Getable<T> key : keys) {
                T interval = key.get();
                if (interval == null) continue;
                insertedNode.addInterval(interval);
              }
            }
            assert marker.isValid();
          }
          else {
            node.setValid(false);
          }
        }
      }
      checkMax(true);

      IntervalNode root = getRoot();
      assert root == null || root.maxEnd + root.delta <= myDocument.getTextLength();
    }
    finally {
      l.writeLock().unlock();
      long finish = System.currentTimeMillis();
    }
  }

  // returns true if all deltas involved are still 0
  private boolean collectAffectedMarkers(IntervalNode root, @NotNull DocumentEvent e, @NotNull List<IntervalNode> affected) {
    if (root == null) return true;
    boolean norm = pushDelta(root);

    int maxEnd = root.maxEnd;
    assert root.isValid();

    int offset = e.getOffset();
    int affectedEndOffset = offset + e.getOldLength();
    boolean hasAliveKeys = root.hasAliveKey(false);
    if (!hasAliveKeys) {
      // marker was garbage collected
      affected.add(root);
    }
    if (offset > maxEnd) {
      // no need to bother
    }
    else if (affectedEndOffset < root.intervalStart()) {
      int lengthDelta = e.getNewLength() - e.getOldLength();
      int newD = root.delta += lengthDelta;
      norm &= newD == 0;
      IntervalNode left = root.getLeft();
      if (left != null) {
        int newL = left.delta -= lengthDelta;
        norm &= newL == 0;
      }
      norm &= pushDelta(root);
      norm &= collectAffectedMarkers(left, e, affected);
      correctMax(root, 0);
    }
    else {
      if (offset <= root.intervalEnd()) {
        // unlucky enough so that change affects the interval
        if (hasAliveKeys) affected.add(root); // otherwise we've already added it
        root.setValid(false);  //make invisible
      }

      norm &= collectAffectedMarkers(root.getLeft(), e, affected);
      norm &= collectAffectedMarkers(root.getRight(), e, affected);
      correctMax(root,0);
    }
    return norm;
  }

  public boolean sweep(final int start, final int end, @NotNull final MarkupModelEx.SweepProcessor<T> sweepProcessor) {
    normalize();
    return sweep(new Generator<T>() {
      @Override
      public boolean generate(Processor<T> processor) {
        return processOverlappingWith(start, end, processor);
      }
    }, sweepProcessor);
  }

  public interface Generator<T> {
    boolean generate(Processor<T> processor);
  }

  public static <T extends Segment> boolean sweep(@NotNull Generator<T> generator, @NotNull final MarkupModelEx.SweepProcessor<T> sweepProcessor) {
    final Queue<T> ends = new PriorityQueue<T>(5, new Comparator<T>() {
      public int compare(T o1, T o2) {
        return o1.getEndOffset() - o2.getEndOffset();
      }
    });
    final List<T> starts = new ArrayList<T>();
    if (!generator.generate(new Processor<T>() {
      public boolean process(T marker) {
        // decide whether previous marker ends here or new marker begins
        int start = marker.getStartOffset();
        while (true) {
          assert ends.size() == starts.size();
          T previous = ends.peek();
          if (previous != null) {
            int prevEnd = previous.getEndOffset();
            if (prevEnd <= start) {
              if (!sweepProcessor.process(prevEnd, previous, false, ends)) return false;
              ends.remove();
              boolean removed = starts.remove(previous);
              assert removed;
              continue;
            }
          }
          break;
        }
        if (!sweepProcessor.process(start, marker, true, ends)) return false;
        starts.add(marker);
        ends.offer(marker);

        return true;
      }
    })) return false;

    while (!ends.isEmpty()) {
      assert ends.size() == starts.size();
      T previous = ends.remove();
      int prevEnd = previous.getEndOffset();
      if (!sweepProcessor.process(prevEnd, previous, false, ends)) return false;
      boolean removed = starts.remove(previous);
      assert removed;
    }

    return true;
  }
}
