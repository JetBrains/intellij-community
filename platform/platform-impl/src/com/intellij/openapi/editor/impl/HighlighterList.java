package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.util.containers.SortedList;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public abstract class HighlighterList {
  private final List<RangeHighlighterImpl> mySegmentHighlighters = new SortedList<RangeHighlighterImpl>(MY_RANGE_COMPARATOR) {
    @Override
    protected void sort(List<RangeHighlighterImpl> delegate) {
      Iterator<RangeHighlighterImpl> it = delegate.iterator();

      while (it.hasNext()) {
        RangeHighlighterImpl highlighter = it.next();
        if (!highlighter.isValid()) {
          it.remove();
        }
      }

      super.sort(delegate);
    }
  };

  private boolean myIsDirtied = false;
  private final DocumentAdapter myDocumentListener;
  private final Document myDoc;
  private int myLongestHighlighterLength = 0;

  private static final Comparator<RangeHighlighterImpl> MY_RANGE_COMPARATOR = new Comparator<RangeHighlighterImpl>() {
    public int compare(RangeHighlighterImpl r1, RangeHighlighterImpl r2) {
      if (r1.getAffectedAreaStartOffset() != r2.getAffectedAreaStartOffset()) {
        return r1.getAffectedAreaStartOffset() - r2.getAffectedAreaStartOffset();
      }

      if (r1.getLayer() != r2.getLayer()) {
        return r2.getLayer() - r1.getLayer();
      }

      return (int) (r2.getId() - r1.getId());
    }
  };

  public HighlighterList(Document doc) {
    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        myIsDirtied = true;
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
