/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.util.containers.SortedList;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public abstract class HighlighterList {
  private final SortedList<RangeHighlighterImpl> mySegmentHighlighters = new SortedList<RangeHighlighterImpl>(MY_RANGE_COMPARATOR) {
    @Override
    protected void sort(List<RangeHighlighterImpl> delegate) {
      Iterator<RangeHighlighterImpl> it = delegate.iterator();
      boolean needSort = false;
      RangeHighlighterImpl lastHighlighter = null;

      while (it.hasNext()) {
        RangeHighlighterImpl highlighter = it.next();
        if (!highlighter.isValid()) {
          needSort = true;
          it.remove();
        }

        if (lastHighlighter != null) {
          if (!needSort) needSort = MY_RANGE_COMPARATOR.compare(lastHighlighter, highlighter) > 0;
        }
        
        lastHighlighter = highlighter;
      }

      if (needSort) {
        super.sort(delegate);
      }
    }

    @Override
    public boolean remove(Object o) {
      if (o instanceof RangeHighlighterImpl) {
        if (!((RangeHighlighterImpl)o).isValid()) return false;
      }
      return super.remove(o);
    }
  };

  private boolean myIsDirtied = false;
  private final DocumentListener myDocumentListener;
  private final Document myDoc;
  private int myLongestHighlighterLength = 0;

  private static final Comparator<RangeHighlighterImpl> MY_RANGE_COMPARATOR = new Comparator<RangeHighlighterImpl>() {
    public int compare(RangeHighlighterImpl r1, RangeHighlighterImpl r2) {
      int o = r1.getAffectedAreaStartOffset() - r2.getAffectedAreaStartOffset();
      if (o != 0) {
        return o;
      }

      if (r1.getLayer() != r2.getLayer()) {
        return r2.getLayer() - r1.getLayer();
      }

      return (int) (r2.getId() - r1.getId());
    }
  };

  public HighlighterList(Document doc) {
    myDocumentListener = new PrioritizedDocumentListener() {
      public int getPriority() {
        return 0; // Need to make sure we invalidate all the stuff before someone (like LineStatusTracker) starts to modify highlights.
      }

      public void beforeDocumentChange(DocumentEvent event) {}

      public void documentChanged(DocumentEvent e) {
        myIsDirtied = true;
        mySegmentHighlighters.markDirty();
      }
    };
    myDoc = doc;
    myDoc.addDocumentListener(myDocumentListener);
  }

  public void dispose() {
    myDoc.removeDocumentListener(myDocumentListener);
  }

  public int getLongestHighlighterLength() {
    return myLongestHighlighterLength;
  }

  private void sortMarkers() {
    assertDispatchThread();
    myLongestHighlighterLength = 0;

    Iterator<RangeHighlighterImpl> iterator = mySegmentHighlighters.iterator();
    while (iterator.hasNext()) {
      RangeHighlighter rangeHighlighter = iterator.next();
      if (rangeHighlighter.isValid()) {
        myLongestHighlighterLength = Math.max(myLongestHighlighterLength, rangeHighlighter.getEndOffset() - rangeHighlighter.getStartOffset());
      }
      else {
        iterator.remove();
      }
    }

    myIsDirtied = false;
  }

  protected abstract void assertDispatchThread();

  Iterator<RangeHighlighterImpl> getHighlighterIterator() {
    if (myIsDirtied) sortMarkers();
    return mySegmentHighlighters.iterator();
  }

  List<RangeHighlighterImpl> getSortedHighlighters() {
    if (myIsDirtied) sortMarkers();
    return mySegmentHighlighters;
  }

  void addSegmentHighlighter(RangeHighlighter segmentHighlighter) {
    assertDispatchThread();
    myIsDirtied = true;
    mySegmentHighlighters.add((RangeHighlighterImpl)segmentHighlighter);
  }

  void removeSegmentHighlighter(RangeHighlighter segmentHighlighter) {
    assertDispatchThread();
    myIsDirtied = true;

    //noinspection SuspiciousMethodCalls
    mySegmentHighlighters.remove(segmentHighlighter);
  }
}
