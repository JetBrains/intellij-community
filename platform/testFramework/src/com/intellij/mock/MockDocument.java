// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class MockDocument extends UserDataHolderBase implements DocumentEx {
  private StringBuffer myText = new StringBuffer();
  private long myModStamp = LocalTimeCounter.currentTime();

  public MockDocument() {
  }

  @Override
  public @NotNull CharSequence getImmutableCharSequence() {
    return myText.toString();
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    myText = new StringBuffer();
    myText.append(chars);
    myModStamp = newModificationStamp;
  }

  public CharSequence textToCharArray() {
    return getText();
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  @Override
  public int getLineCount() {
    return 1;
  }

  @Override
  public int getLineNumber(int offset) {
    return 0;
  }

  @Override
  public int getLineStartOffset(int line) {
    return 0;
  }

  @Override
  public int getLineEndOffset(int line) {
    return myText.length();
  }

  @Override
  public void insertString(int offset, @NotNull CharSequence s) {
    myText.insert(offset, s);
  }

  @Override
  public void deleteString(int startOffset, int endOffset) {
    myText.delete(startOffset, endOffset);
  }

  @Override
  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    myText.replace(startOffset, endOffset, s.toString());
    myModStamp = LocalTimeCounter.currentTime();
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public long getModificationStamp() {
    return myModStamp;
  }

  @Override
  public @NotNull RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull LineIterator createLineIterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setModificationStamp(long modificationStamp) {
    myModStamp = modificationStamp;
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<? super RangeMarker> processor) {
    return false;
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<? super RangeMarker> processor) {
    return false;
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return false;
  }
}
