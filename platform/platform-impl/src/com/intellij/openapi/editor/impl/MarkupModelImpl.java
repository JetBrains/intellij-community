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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MarkupModelImpl extends UserDataHolderBase implements MarkupModelEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.MarkupModelImpl");
  private final DocumentImpl myDocument;

  private RangeHighlighter[] myCachedHighlighters;
  private final List<MarkupModelListener> myListeners = ContainerUtil.createEmptyCOWList();
  private final RangeHighlighterTree myHighlighterTree;

  MarkupModelImpl(DocumentImpl document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDocument = document;
    myHighlighterTree = new RangeHighlighterTree(myDocument);
  }

  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myHighlighterTree.dispose();
  }

  @NotNull
  public RangeHighlighter addLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    if (lineNumber >= getDocument().getLineCount() || lineNumber < 0) {
      throw new IndexOutOfBoundsException("lineNumber:" + lineNumber + ". Must be in [0, " + (getDocument().getLineCount() - 1) + "]");
    }

    int offset = getFirstNonspaceCharOffset(getDocument(), lineNumber);

    return addRangeHighlighter(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  public RangeHighlighter addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    if (lineNumber >= getDocument().getLineCount() || lineNumber < 0) return null;

    int offset = getFirstNonspaceCharOffset(getDocument(), lineNumber);

    return addRangeHighlighterAndChangeAttributes(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE, true, null);
  }

  private static int getFirstNonspaceCharOffset(Document doc, int lineNumber) {
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

  // NB: Can return invalid highlighters
  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myCachedHighlighters == null) {
      int size = myHighlighterTree.size();
      if (size == 0) return RangeHighlighter.EMPTY_ARRAY;
      List<RangeHighlighterEx> list = new ArrayList<RangeHighlighterEx>(size);
      myHighlighterTree.process(new CommonProcessors.CollectProcessor<RangeHighlighterEx>(list));
      myCachedHighlighters = list.toArray(new RangeHighlighter[list.size()]);
    }
    return myCachedHighlighters;
  }

  @Override
  public RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
                                                                   int endOffset,
                                                                   int layer,
                                                                   TextAttributes textAttributes,
                                                                   @NotNull HighlighterTargetArea targetArea,
                                                                   boolean isPersistent,
                                                                   @Nullable Consumer<RangeHighlighterEx> changeAttributesAction) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    RangeHighlighterEx highlighter = isPersistent
                                     ? new PersistentRangeHighlighterImpl(this, startOffset, layer, targetArea, textAttributes)
                                     : new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea, textAttributes, false, false);

    myCachedHighlighters = null;
    if (changeAttributesAction != null) {
      ((RangeHighlighterImpl)highlighter).changeAttributesNoEvents(changeAttributesAction);
    }
    fireAfterAdded(highlighter);
    return highlighter;
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter, @NotNull Consumer<RangeHighlighterEx> changeAttributesAction) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean changed = ((RangeHighlighterImpl)highlighter).changeAttributesNoEvents(changeAttributesAction);
    if (changed) {
      fireAttributesChanged(highlighter);
    }
  }

  IntervalTreeImpl.IntervalNode addRangeHighlighter(RangeHighlighterEx marker,
                                                    int start,
                                                    int end,
                                                    boolean greedyToLeft,
                                                    boolean greedyToRight,
                                                    int layer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myHighlighterTree.addInterval(marker, start, end, greedyToLeft, greedyToRight, layer);
  }

  @NotNull
  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              TextAttributes textAttributes,
                                              @NotNull HighlighterTargetArea targetArea) {
    return addRangeHighlighterAndChangeAttributes(startOffset, endOffset, layer, textAttributes, targetArea, false, null);
  }

  public void removeHighlighter(@NotNull RangeHighlighter segmentHighlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myCachedHighlighters = null;
    if (!segmentHighlighter.isValid()) return;

    fireBeforeRemoved((RangeHighlighterEx)segmentHighlighter);

    boolean removed = myHighlighterTree.removeInterval((RangeHighlighterEx)segmentHighlighter);
    LOG.assertTrue(removed);
  }

  public void removeAllHighlighters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myHighlighterTree.process(new Processor<RangeMarkerEx>() {
      public boolean process(RangeMarkerEx rangeMarkerEx) {
        fireBeforeRemoved((RangeHighlighterEx)rangeMarkerEx);
        return true;
      }
    });
    myCachedHighlighters = null;
    myHighlighterTree.clear();
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  public void addMarkupModelListener(@NotNull MarkupModelListener listener) {
    myListeners.add(listener);
  }

  public void removeMarkupModelListener(@NotNull MarkupModelListener listener) {
    boolean success = myListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void setRangeHighlighterAttributes(@NotNull final RangeHighlighter highlighter, final TextAttributes textAttributes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((RangeHighlighterImpl)highlighter).setTextAttributes(textAttributes);
  }

  protected void fireAttributesChanged(RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.attributesChanged(segmentHighlighter);
    }
  }
  private void fireAfterAdded(RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.afterAdded(segmentHighlighter);
    }
  }
  private void fireBeforeRemoved(RangeHighlighterEx segmentHighlighter) {
    for (MarkupModelListener listener : myListeners) {
      listener.beforeRemoved(segmentHighlighter);
    }
  }

  public boolean containsHighlighter(@NotNull final RangeHighlighter highlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return !myHighlighterTree.processOverlappingWith(highlighter.getStartOffset(), highlighter.getEndOffset(), new Processor<RangeHighlighterEx>() {
      public boolean process(RangeHighlighterEx h) {
        return h.getId() != ((RangeHighlighterEx)highlighter).getId();
      }
    });
  }

  public boolean processHighlightsOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return myHighlighterTree.processOverlappingWith(start, end, processor);
  }

  @NotNull
  public Iterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    return myHighlighterTree.overlappingIterator(startOffset, endOffset);
  }

  public boolean sweep(int start, int end, @NotNull SweepProcessor<RangeHighlighterEx> sweepProcessor) {
    return myHighlighterTree.sweep(start, end, sweepProcessor);
  }

  public void normalize() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myHighlighterTree.normalize();
  }
}
