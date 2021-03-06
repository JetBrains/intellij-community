// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EditorFilteringMarkupModelEx implements MarkupModelEx {
  @NotNull private final EditorImpl myEditor;
  @NotNull private final MarkupModelEx myDelegate;

  private final Condition<RangeHighlighter> IS_AVAILABLE = this::isAvailable;

  EditorFilteringMarkupModelEx(@NotNull EditorImpl editor, @NotNull MarkupModelEx delegate) {
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
    FilteringProcessor<RangeHighlighterEx> filteringProcessor = new FilteringProcessor<>(IS_AVAILABLE, processor);
    return myDelegate.processRangeHighlightersOverlappingWith(start, end, filteringProcessor);
  }

  @Override
  public boolean processRangeHighlightersOutside(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    FilteringProcessor<RangeHighlighterEx> filteringProcessor = new FilteringProcessor<>(IS_AVAILABLE, processor);
    return myDelegate.processRangeHighlightersOutside(start, end, filteringProcessor);
  }

  @Override
  @NotNull
  public MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset, int endOffset) {
    return new FilteringMarkupIterator<>(myDelegate.overlappingIterator(startOffset, endOffset), this::isAvailable);
  }

  @NotNull
  @Override
  public MarkupIterator<RangeHighlighterEx> overlappingIterator(int startOffset,
                                                                int endOffset,
                                                                boolean onlyRenderedInGutter) {
    return new FilteringMarkupIterator<>(myDelegate.overlappingIterator(startOffset, endOffset, onlyRenderedInGutter), this::isAvailable);
  }

  @Override
  public RangeHighlighter @NotNull [] getAllHighlighters() {
    List<RangeHighlighter> list = ContainerUtil.filter(myDelegate.getAllHighlighters(), IS_AVAILABLE);
    return list.toArray(RangeHighlighter.EMPTY_ARRAY);
  }

  @Override
  public void addMarkupModelListener(@NotNull Disposable parentDisposable, @NotNull MarkupModelListener listener) {
    myDelegate.addMarkupModelListener(parentDisposable, listener);
  }

  @Override
  public void dispose() {
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
  public void fireAttributesChanged(@NotNull RangeHighlighterEx segmentHighlighter,
                                    boolean renderersChanged, boolean fontStyleOrColorChanged) {
    myDelegate.fireAttributesChanged(segmentHighlighter, renderersChanged, fontStyleOrColorChanged);
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
  public RangeHighlighterEx addPersistentLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int lineNumber, int layer) {
    return myDelegate.addPersistentLineHighlighter(textAttributesKey, lineNumber, layer);
  }

  @Override
  public @Nullable RangeHighlighterEx addPersistentLineHighlighter(int lineNumber, int layer, @Nullable TextAttributes textAttributes) {
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
  public RangeHighlighter addRangeHighlighter(@Nullable TextAttributesKey textAttributesKey, int startOffset,
                                              int endOffset,
                                              int layer,
                                              @NotNull HighlighterTargetArea targetArea) {
    return myDelegate.addRangeHighlighter(textAttributesKey, startOffset, endOffset, layer, targetArea);
  }

  @Override
  public @NotNull RangeHighlighter addRangeHighlighter(int startOffset,
                                                       int endOffset,
                                                       int layer,
                                                       @Nullable TextAttributes textAttributes,
                                                       @NotNull HighlighterTargetArea targetArea) {
    return myDelegate.addRangeHighlighter(startOffset, endOffset, layer, textAttributes, targetArea);
  }

  @Override
  @NotNull
  public RangeHighlighter addLineHighlighter(@Nullable TextAttributesKey textAttributesKey, int line, int layer) {
    return myDelegate.addLineHighlighter(textAttributesKey, line, layer);
  }

  @Override
  public @NotNull RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    return myDelegate.addLineHighlighter(line, layer, textAttributes);
  }

  @Override
  @NotNull
  public RangeHighlighterEx addRangeHighlighterAndChangeAttributes(@Nullable TextAttributesKey textAttributesKey,
                                                                   int startOffset,
                                                                   int endOffset,
                                                                   int layer,
                                                                   @NotNull HighlighterTargetArea targetArea,
                                                                   boolean isPersistent,
                                                                   @Nullable Consumer<? super RangeHighlighterEx> changeAttributesAction) {
    return myDelegate.addRangeHighlighterAndChangeAttributes(textAttributesKey, startOffset, endOffset, layer,
                                                             targetArea, isPersistent, changeAttributesAction);
  }

  @Override
  public void setRangeHighlighterAttributes(@NotNull RangeHighlighter highlighter, @NotNull TextAttributes textAttributes) {
    myDelegate.setRangeHighlighterAttributes(highlighter, textAttributes);
  }

  @Override
  public void changeAttributesInBatch(@NotNull RangeHighlighterEx highlighter,
                                      @NotNull Consumer<? super RangeHighlighterEx> changeAttributesAction) {
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
