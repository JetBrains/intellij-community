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
import com.intellij.openapi.util.Trinity;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: cdr
 */
public class RangeMarkerTree<T extends RangeMarkerEx> extends IntervalTreeImpl<T> {
  private static final EqualStartIntervalComparator<RangeMarkerEx> RANGEMARKER_COMPARATOR = new EqualStartIntervalComparator<RangeMarkerEx>() {
    public int compare(RangeMarkerEx o1, RangeMarkerEx o2) {
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

  protected RangeMarkerTree(Document document, EqualStartIntervalComparator<T> comparator) {
    super(comparator);

    document.addDocumentListener(new PrioritizedDocumentListener() {
      public int getPriority() {
        return EditorDocumentPriorities.RANGE_MARKER; // Need to make sure we invalidate all the stuff before someone (like LineStatusTracker) starts to modify highlights.
      }

      public void beforeDocumentChange(DocumentEvent event) {}

      public void documentChanged(DocumentEvent e) {
        updateMarkersOnChange(e);
      }
    });
  }

  public RangeMarkerTree(Document document) {
    this(document, (EqualStartIntervalComparator<T>)RANGEMARKER_COMPARATOR);
  }

  @Override
  protected Trinity<Integer, Integer, Integer> checkMax(MyNode root, int deltaUpToRootExclusive, boolean assertInvalid) {
    if (root != null) {
      RangeMarkerImpl r = (RangeMarkerImpl)root.key;
      assert r.myNode == null || r.myNode == root;
    }
    return super.checkMax(root, deltaUpToRootExclusive, assertInvalid);
  }

  private void updateMarkersOnChange(DocumentEvent e) {
    try {
      l.writeLock().lock();
      checkMax(true);

      modCount++;
      List<MyNode> affected = new ArrayList<MyNode>();
      updateMarkersOnChange(getRoot(), e, affected);
      checkMax(false);

      for (MyNode node : affected) {
        // assumption: interval.getEndOffset() will never be accessed during remove()
        RangeMarkerEx marker = node.key;
        int startOffset = marker.getStartOffset();
        int endOffset = marker.getEndOffset();
        deleteNode(node);
        node.delta = 0;
        node.setParent(null);
        node.setLeft(null);
        node.setRight(null);
        assert marker.intervalStart() == startOffset;
        assert marker.intervalEnd() == endOffset;
        //marker.setIntervalStart(startOffset);
        //marker.setIntervalEnd(endOffset); //might have been changed by delete
      }
      checkMax(true);
      for (MyNode node : affected) {
        RangeMarkerImpl marker = (RangeMarkerImpl)node.key;
        marker.setValid(true);
        marker.myNode = null;
        marker.documentChanged(e);
        if (marker.isValid()) {
          marker.registerInDocument();
        }
      }
      checkMax(true);
    }
    finally {
      l.writeLock().unlock();
    }
  }

  private void updateMarkersOnChange(MyNode root,
                                     DocumentEvent e,
                                     List<MyNode> affected) {
    if (root == null) return;
    pushDelta(root);

    int maxEnd = root.maxEnd;
    RangeMarkerEx interval = root.key;
    assert interval.isValid();

    int offset = e.getOffset();
    int affectedEndOffset = offset + e.getOldLength();
    int lengthDelta = e.getNewLength() - e.getOldLength();
    if (offset > maxEnd) {

    }
    else if (affectedEndOffset < interval.intervalStart()) {
      root.delta += lengthDelta;
      if (root.getLeft() != null) {
        root.getLeft().delta -= lengthDelta;
      }
      pushDelta(root);
      updateMarkersOnChange(root.getLeft(), e, affected);
      correctMax(root, 0);
    }
    else {
      if (offset <= interval.getEndOffset()) {
        // unlucky enough so that change affects the interval
        affected.add(root);
        root.key.setValid(false);  //make invisible
      }

      updateMarkersOnChange(root.getLeft(), e, affected);
      updateMarkersOnChange(root.getRight(), e, affected);
      correctMax(root,0);
    }
  }


  public boolean sweep(final int start, final int end, @NotNull final MarkupModelEx.SweepProcessor<T> sweepProcessor) {
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
