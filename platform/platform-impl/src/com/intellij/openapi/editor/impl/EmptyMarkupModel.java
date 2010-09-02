/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * This is mock implementation to be used in null-object pattern where necessary.
 * @author max
 */
public class EmptyMarkupModel implements MarkupModelEx {
  private final Document myDocument;

  public EmptyMarkupModel(final Document document) {
    myDocument = document;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              @Nullable TextAttributes textAttributes,
                                              @NotNull HighlighterTargetArea targetArea) {
    throw new ProcessCanceledException();
  }

  @NotNull
  public RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    throw new ProcessCanceledException();
  }

  public void removeHighlighter(RangeHighlighter rangeHighlighter) {
  }

  public void removeAllHighlighters() {
  }

  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    return RangeHighlighter.EMPTY_ARRAY;
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  public <T> void putUserData(@NotNull Key<T> key, T value) {
  }

  public void dispose() {
  }

  public RangeHighlighter addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    return null;
  }

  public boolean containsHighlighter(@NotNull RangeHighlighter highlighter) {
    return false;
  }

  public void addMarkupModelListener(@NotNull MarkupModelListener listener) {
  }

  public void removeMarkupModelListener(@NotNull MarkupModelListener listener) {
  }

  public void setRangeHighlighterAttributes(@NotNull final RangeHighlighter highlighter, final TextAttributes textAttributes) {

  }

  public boolean processHighlightsOverlappingWith(int start, int end, @NotNull Processor<? super RangeHighlighterEx> processor) {
    return false;
  }

  public Iterator<RangeHighlighterEx> iterator() {
    return ContainerUtil.emptyIterator();
  }

  @NotNull
  public Iterator<RangeHighlighterEx> iteratorFrom(@NotNull Interval interval) {
    return iterator();
  }
  public boolean sweep(int start, int end, @NotNull SweepProcessor<RangeHighlighterEx> sweepProcessor) {
    return false;
  }
}
