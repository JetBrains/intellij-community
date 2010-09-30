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
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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
    myDocument = document;
    myHighlighterTree = new RangeHighlighterTree(myDocument);
  }

  protected void assertDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  public void dispose() {
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
      ArrayList<RangeHighlighterEx> list = new ArrayList<RangeHighlighterEx>();
      myHighlighterTree.process(new CommonProcessors.CollectProcessor<RangeHighlighterEx>(list));
      myCachedHighlighters = list.toArray(new RangeHighlighter[list.size()]);
    }
    return myCachedHighlighters;
  }

  private RangeHighlighter addRangeHighlighter(int startOffset,
                                               int endOffset,
                                               int layer,
                                               TextAttributes textAttributes,
                                               HighlighterTargetArea targetArea,
                                               boolean isPersistent) {
    RangeHighlighterEx segmentHighlighter = isPersistent
                                            ? new PersistentRangeHighlighterImpl(this, startOffset, layer, targetArea, textAttributes)
                                            : new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea, textAttributes);

    RangeMarkerImpl marker = (RangeMarkerImpl)segmentHighlighter;
    marker.registerInDocument();

    myCachedHighlighters = null;
    fireAfterAdded(segmentHighlighter);
    return segmentHighlighter;
  }

  void addRangeHighlighter(RangeHighlighterEx marker) {
    marker.setValid(true);
    ((RangeMarkerImpl)marker).myNode = (IntervalTreeImpl.MyNode)myHighlighterTree.add(marker);
    myHighlighterTree.checkMax(true);
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
    myCachedHighlighters = null;
    if (!segmentHighlighter.isValid()) return;

    fireBeforeRemoved((RangeHighlighterEx)segmentHighlighter);

    boolean removed = myHighlighterTree.remove((RangeHighlighterEx)segmentHighlighter);
    LOG.assertTrue(removed);
  }

  public void removeAllHighlighters() {
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
    return !myHighlighterTree.processOverlappingWith(highlighter.getStartOffset(), highlighter.getEndOffset(), new Processor<RangeHighlighterEx>() {
      public boolean process(RangeHighlighterEx h) {
        return h.getId() != ((RangeHighlighterEx)highlighter).getId();
      }
    });
  }

  public boolean processHighlightsOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return myHighlighterTree.processOverlappingWith(start, end, processor);
  }

  public Iterator<RangeHighlighterEx> iterator() {
    return myHighlighterTree.iterator();
  }

  @NotNull
  public Iterator<RangeHighlighterEx> iteratorFrom(@NotNull Interval interval) {
    return myHighlighterTree.iteratorFrom(interval);
  }

  public boolean sweep(int start, int end, @NotNull SweepProcessor<RangeHighlighterEx> sweepProcessor) {
    return myHighlighterTree.sweep(start, end, sweepProcessor);
  }
  public void normalize() {
    myHighlighterTree.normalize();
  }
}
