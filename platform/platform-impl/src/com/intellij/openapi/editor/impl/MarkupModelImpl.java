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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:26:19 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.event.MarkupModelEvent;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MarkupModelImpl extends UserDataHolderBase implements MarkupModelEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.MarkupModelImpl");
  private final DocumentImpl myDocument;

  private final HighlighterList myHighlighterList;
  private final Collection<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();
  private RangeHighlighter[] myCachedHighlighters;
  private final List<MarkupModelListener> myListeners = new ArrayList<MarkupModelListener>();
  private MarkupModelListener[] myCachedListeners;

  MarkupModelImpl(DocumentImpl document) {
    myDocument = document;
    myHighlighterList = new HighlighterList(document) {
      @Override
      protected void assertDispatchThread() {
        MarkupModelImpl.this.assertDispatchThread();
      }
    };
  }

  protected void assertDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  public void dispose() {
    myHighlighterList.dispose();
  }

  @NotNull
  public RangeHighlighter addLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    if (lineNumber >= getDocument().getLineCount() || lineNumber < 0) {
      throw new IndexOutOfBoundsException("lineNumber:" + lineNumber + ". Shold be in [0, " + (getDocument().getLineCount() - 1) + "]");
    }

    int offset = getFirstNonspaceCharOffset(getDocument(), lineNumber);

    return addRangeHighlighter(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  public RangeHighlighter addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    if (lineNumber >= getDocument().getLineCount() || lineNumber < 0) return null;

    int offset = getFirstNonspaceCharOffset(getDocument(), lineNumber);

    return addRangeHighlighter(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE, true);
  }

  static int getFirstNonspaceCharOffset(Document doc, int lineNumber) {
    int lineStart = doc.getLineStartOffset(lineNumber);
    int lineEnd = doc.getLineEndOffset(lineNumber);
    CharSequence text = doc.getCharsSequence();
    int offset = lineStart;
    for (int i = lineStart; i < lineEnd; i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t') {
        offset = i;
        break;
      }
    }
    return offset;
  }

  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    if (myCachedHighlighters == null) {
      myCachedHighlighters = myHighlighters.toArray(new RangeHighlighter[myHighlighters.size()]);
    }
    return myCachedHighlighters;
  }

  private RangeHighlighter addRangeHighlighter(int startOffset,
                                               int endOffset,
                                               int layer,
                                               TextAttributes textAttributes,
                                               HighlighterTargetArea targetArea,
                                               boolean isPersistent) {
    RangeHighlighterImpl segmentHighlighter = new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea,
                                                                       textAttributes, isPersistent);
    myHighlighters.add(segmentHighlighter);
    myCachedHighlighters = null;
    myHighlighterList.addSegmentHighlighter(segmentHighlighter);
    fireSegmentHighlighterChanged(segmentHighlighter);
    return segmentHighlighter;

  }

  @NotNull
  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              TextAttributes textAttributes,
                                              @NotNull HighlighterTargetArea targetArea) {
    return addRangeHighlighter(startOffset, endOffset, layer, textAttributes, targetArea, false);
  }

  public void removeHighlighter(RangeHighlighter segmentHighlighter) {
    boolean removed = myHighlighters.remove(segmentHighlighter);
    LOG.assertTrue(removed);
    myCachedHighlighters = null;
    myHighlighterList.removeSegmentHighlighter(segmentHighlighter);
    fireSegmentHighlighterChanged(segmentHighlighter);
  }

  public void removeAllHighlighters() {
    for (RangeHighlighter highlighter : myHighlighters) {
      fireSegmentHighlighterChanged(highlighter);
      myHighlighterList.removeSegmentHighlighter(highlighter);
    }
    myHighlighters.clear();
    myCachedHighlighters = null;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  public void addMarkupModelListener(MarkupModelListener listener) {
    myListeners.add(listener);
    myCachedListeners = null;
  }

  public void removeMarkupModelListener(MarkupModelListener listener) {
    boolean success = myListeners.remove(listener);
    LOG.assertTrue(success);
    myCachedListeners = null;
  }

  public void setRangeHighlighterAttributes(final RangeHighlighter highlighter, final TextAttributes textAttributes) {
    ((RangeHighlighterImpl)highlighter).setTextAttributes(textAttributes);
  }

  private MarkupModelListener[] getCachedListeners() {
    if (myCachedListeners == null) {
      myCachedListeners = myListeners.isEmpty() ? MarkupModelListener.EMPTY_ARRAY : myListeners.toArray(new MarkupModelListener[myListeners.size()]);
    }
    return myCachedListeners;
  }

  protected void fireSegmentHighlighterChanged(RangeHighlighter segmentHighlighter) {
    MarkupModelEvent event = new MarkupModelEvent(this, segmentHighlighter);
    MarkupModelListener[] listeners = getCachedListeners();
    for (MarkupModelListener listener : listeners) {
      listener.rangeHighlighterChanged(event);
    }
  }

  public HighlighterList getHighlighterList() {
    return myHighlighterList;
  }

  public boolean containsHighlighter(RangeHighlighter highlighter) {
    return myHighlighters.contains(highlighter);
  }
}
