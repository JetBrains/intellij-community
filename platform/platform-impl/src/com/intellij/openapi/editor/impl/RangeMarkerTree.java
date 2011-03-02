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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: cdr
 */
public class RangeMarkerTree<T extends RangeMarkerEx> extends IntervalTreeImpl<T> {
  private final PrioritizedDocumentListener myListener;
  private final Document myDocument;
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

      // for now we tolerate equal range range markers (till lazy creation impl)
      d = (int)(o1.getId() - o2.getId());
      return d;
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
  protected Trinity<Integer, Integer, Integer> checkMax(IntervalNode root,
                                                        int deltaUpToRootExclusive,
                                                        boolean assertInvalid,
                                                        Ref<Boolean> allValid, AtomicInteger counter) {
    if (root != null) {
      RangeMarkerImpl r = (RangeMarkerImpl)root.getKey();
      if (r != null) {
        assert /*r.myNode == null || */r.myNode == root;
        assert r.myNode.getTree() == this;
      }
    }
    return super.checkMax(root, deltaUpToRootExclusive, assertInvalid, allValid, counter);
  }

  @Override
  public IntervalNode addInterval(@NotNull T interval, int start, int end, Object data) {
    RangeMarkerImpl marker = (RangeMarkerImpl)interval;
    marker.setValid(true);
    return super.addInterval(interval, start, end, data);
  }

  @Override
  protected RMNode createNewNode(T key, int start, int end, Object data) {
    RMNode node = new RMNode(key, start, end);
    ((RangeMarkerImpl)key).myNode = node;
    return node;
  }

  //private static long counter;
  private static final AtomicLong counter = new AtomicLong();
  public class RMNode extends MyNode {
    private boolean isExpandToLeft = false;
    private boolean isExpandToRight = false;
    private final long myId;

    public RMNode(@NotNull T key, int start, int end) {
      super(key, start, end);
      myId = counter.getAndIncrement();
    //myId = counter++;
    }

    public void setGreedyToLeft(boolean greedy) {
      isExpandToLeft = greedy;
    }

    public void setGreedyToRight(boolean greedy) {
      isExpandToRight = greedy;
    }

    public boolean isGreedyToLeft() {
      return isExpandToLeft;
    }

    public boolean isGreedyToRight() {
      return isExpandToRight;
    }

    public long getId() {
      return myId;
    }

    @Override
    public String toString() {
      return (isGreedyToLeft() ? "[" : "(") + intervalStart() + "," + intervalEnd() + (isGreedyToRight() ? "]" : ")");
    }
  }

  private void updateMarkersOnChange(DocumentEvent e) {
    try {
      l.writeLock().lock();
      checkMax(true);
      normalized = false;

      modCount++;
      List<IntervalNode> affected = new ArrayList<IntervalNode>();
      updateMarkersOnChange(getRoot(), e, affected);
      checkMax(false);

      if (!affected.isEmpty()) {
        for (IntervalNode node : affected) {
          // assumption: interval.getEndOffset() will never be accessed during remove()
          int startOffset = node.intervalStart();
          int endOffset = node.intervalEnd();
          removeNode(node);
          node.delta = 0;
          node.setParent(null);
          node.setLeft(null);
          node.setRight(null);
          assert node.intervalStart() == startOffset;
          assert node.intervalEnd() == endOffset;
        }
        checkMax(true);
        for (IntervalNode node : affected) {
          RangeMarkerImpl marker = (RangeMarkerImpl)node.getKey();
          if (marker == null) continue; // collected
          marker.setValid(true);
          //marker.myNode = null;
          marker.documentChanged(e);
          if (marker.isValid()) {
            insert(node);
          }
        }
        checkMax(true);
      }

      IntervalNode root = getRoot();
      assert root == null || root.maxEnd + root.delta <= myDocument.getTextLength();
    }
    finally {
      l.writeLock().unlock();
    }
  }

  private static void updateMarkersOnChange(IntervalNode root,
                                            @NotNull DocumentEvent e,
                                            @NotNull List<IntervalNode> affected) {
    if (root == null) return;
    pushDelta(root);

    int maxEnd = root.maxEnd;
    assert root.isValid();

    int offset = e.getOffset();
    int affectedEndOffset = offset + e.getOldLength();
    Object key = root.getKey();
    if (key == null) {
      // marker was garbage collected
      affected.add(root);
    }
    if (offset > maxEnd) {

    }
    else if (affectedEndOffset < root.intervalStart()) {
      int lengthDelta = e.getNewLength() - e.getOldLength();
      root.delta += lengthDelta;
      if (root.getLeft() != null) {
        root.getLeft().delta -= lengthDelta;
      }
      pushDelta(root);
      updateMarkersOnChange(root.getLeft(), e, affected);
      correctMax(root, 0);
    }
    else {
      if (offset <= root.intervalEnd()) {
        // unlucky enough so that change affects the interval
        if (key != null) affected.add(root); // otherwise we've already added it
        root.setValid(false);  //make invisible
      }

      updateMarkersOnChange(root.getLeft(), e, affected);
      updateMarkersOnChange(root.getRight(), e, affected);
      correctMax(root,0);
    }
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
