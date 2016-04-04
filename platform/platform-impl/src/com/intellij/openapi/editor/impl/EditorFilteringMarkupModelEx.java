/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import com.intellij.util.FilteringProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EditorFilteringMarkupModelEx implements MarkupModelEx {
  @NotNull private final EditorImpl myEditor;
  @NotNull private final MarkupModelEx myDelegate;

  private final Condition<RangeHighlighter> IS_AVAILABLE = new Condition<RangeHighlighter>() {
    @Override
    public boolean value(RangeHighlighter highlighter) {
      return isAvailable(highlighter);
    }
  };

  public EditorFilteringMarkupModelEx(@NotNull EditorImpl editor, @NotNull MarkupModelEx delegate) {
    myEditor = editor;
    myDelegate = delegate;
  }

  @NotNull
  public MarkupModelEx getDelegate() {
    return myDelegate;
  }

  private boolean isAvailable(@NotNull RangeHighlighter highlighter) {
    return highlighter.getEditorFilter().avaliableIn(myEditor) && myEditor.isHighlighterAvailable(highlighter);
  }

  @Override
  public boolean containsHighlighter(@NotNull RangeHighlighter highlighter) {
    return isAvailable(highlighter) && myDelegate.containsHighlighter(highlighter);
  }

  @Override
  public boolean processRangeHighlightersOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    //noinspection unchecked
    FilteringProcessor<? super RangeHighlighterEx> filteringProcessor = new FilteringProcessor(IS_AVAILABLE, processor);
    return myDelegate.processRangeHighlightersOverlappingWith(start, end, filteringProcessor);
  }

  @Override
  public boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    //noinspection unchecked
    FilteringProcessor<? super RangeHighlighterEx> filteringProcessor = new FilteringProcessor(IS_AVAILABLE, processor);
    return myDelegate.processRangeHighlightersOutside(start, end, filteringProcessor);
  }

  @Override
  @NotNull
  public MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    return new MyFilteringIterator(myDelegate.overlappingIterator(startOffset, endOffset));
  }

  @Override
  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    List<RangeHighlighter> list = ContainerUtil.filter(myDelegate.getAllHighlighters(), IS_AVAILABLE);
    return list.toArray(new RangeHighlighter[list.size()]);
  }

  @Override
  public void dispose() {
  }

  private class MyFilteringIterator extends FilteringIterator<RangeHighlighterEx, RangeHighlighterEx>
    implements MarkupIterator<RangeHighlighterEx> {
    private MarkupIterator<RangeHighlighterEx> myDelegate;

    public MyFilteringIterator(@NotNull MarkupIterator<RangeHighlighterEx> delegate) {
      super(delegate, IS_AVAILABLE);
      myDelegate = delegate;
    }

    @Override
    public void dispose() {
      myDelegate.dispose();
    }
  }

  //
  // Delegated
  //

  @Override
  @NotNull
  public Document getDocument() {
    return myDelegate.getDocument();
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull MarkupModelListener listener) {
    myDelegate.addMarkupModelListener(parentDisposable, listener);
  }

  @Override
  public void fireAttributesChanged(@NotNull RangeHighlighterEx segmentHighlighter, boolean renderersChanged, boolean fontStyleChanged) {
    myDelegate.fireAttributesChanged(segmentHighlighter, renderersChanged, fontStyleChanged);
  }

  @Override
  public void fireAfterAdded(@NotNull RangeHighlighterEx segmentHighlighter) {
    myDelegate.fireAfterAdded(segmentHighlighter);
  }

  @Override
  public void fireBeforeRemoved(@NotNull RangeHighlighterEx segmentHighlighter) {
    myDelegate.fireBeforeRemoved(segmentHighlighter);
  }

  @Override
  @Nullable
  public RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    return myDelegate.addPersistentLineHighlighter(lineNumber, layer, textAttributes);
  }

  @Override
  public void addRangeHighlighter(@NotNull RangeHighlighterEx marker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    myDelegate.addRangeHighlighter(marker, start, end, greedyToLeft, greedyToRight, layer);
  }

  @Override
  @NotNull
  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              @Nullable TextAttributes textAttributes,
                                              @NotNull HighlighterTargetArea targetArea) {
    return myDelegate.addRangeHighlighter(startOffset, endOffset, layer, textAttributes, targetArea);
  }

  @Override
  @NotNull
  public RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    return myDelegate.addLineHighlighter(line, layer, textAttributes);
  }

  @Override
  @NotNull
  public RangeHighlighterEx addRangeHighlighterAndChangeAttributes(int startOffset,
                                                                   int endOffset,
                                                                   int layer,
                                                                   TextAttributes textAttributes,
                                                                   @NotNull HighlighterTargetArea targetArea,
                                                                   boolean isPersistent,
                                                                   Consumer<RangeHighlighterEx> changeAttributesAction) {
    return myDelegate.addRangeHighlighterAndChangeAttributes(startOffset, endOffset, layer, textAttributes, targetArea, isPersistent,
                                                             changeAttributesAction);
  }

  @Override
  public void setRangeHighlighterAttributes(@NotNull RangeHighlighter highlighter, @NotNull TextAttributes textAttributes) {
    myDelegate.setRangeHighlighterAttributes(highlighter, textAttributes);
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<RangeHighlighterEx> changeAttributesAction) {
    myDelegate.changeAttributesInBatch(highlighter, changeAttributesAction);
  }

  @Override
  public void removeHighlighter(@NotNull RangeHighlighter rangeHighlighter) {
    myDelegate.removeHighlighter(rangeHighlighter);
  }

  @Override
  public void removeAllHighlighters() {
    myDelegate.removeAllHighlighters();
  }

  @Override
  @Nullable
  public <T> T getUserData(@NotNull Key<T> key) {
    return myDelegate.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDelegate.putUserData(key, value);
  }
}
