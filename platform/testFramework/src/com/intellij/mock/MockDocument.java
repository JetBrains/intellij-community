/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

  @NotNull
  @Override
  public CharSequence getImmutableCharSequence() {
    return myText.toString();
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    myText = new StringBuffer();
    myText.append(chars);
    myModStamp = newModificationStamp;
  }

  @Override
  public void moveText(int srcStart, int srcEnd, int dstOffset) {
    throw new UnsupportedOperationException();
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

  @NotNull
  @Override
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  @Override
  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  @Override
  public LineIterator createLineIterator() {
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
