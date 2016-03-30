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
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockDocument implements DocumentEx {
  private final Map myUserData = new HashMap();
  private StringBuffer myText = new StringBuffer();
  private long myModStamp = LocalTimeCounter.currentTime();

  public MockDocument() {
  }

  public MockDocument(String text) {
    myText.append(text);
  }

  @NotNull
  @Override
  public String getText() {
    return myText.toString();
  }

  @NotNull
  @Override
  public String getText(@NotNull TextRange range) {
    return range.substring(myText.toString());
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    myText = new StringBuffer();
    myText.append(chars);
    myModStamp = newModificationStamp;
  }

  @Override
  public int getListenersCount() {
    return 0;
  }

  public CharSequence textToCharArray() {
    return getText();
  }

  @Override
  @NotNull
  public char[] getChars() {
    return getText().toCharArray();
  }

  @Override
  @NotNull
  public CharSequence getCharsSequence() {
    return getText();
  }

  @NotNull
  @Override
  public CharSequence getImmutableCharSequence() {
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
  public void moveText(int srcStart, int srcEnd, int dstOffset) {
    // TODO den implement
    throw new UnsupportedOperationException();
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
  public void fireReadOnlyModificationAttempt() {
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener) {
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    return null;
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    return null;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public <T> T getUserData(@NotNull Key<T> key) {
    return (T)myUserData.get(key);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    myUserData.put(key, value);
  }

  @Override
  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
  }

  @Override
  public int getLineSeparatorLength(int line) {
    return 0;
  }

  @Override
  @NotNull
  public LineIterator createLineIterator() {
    return null;
  }

  @Override
  public void setModificationStamp(long modificationStamp) {
    myModStamp = modificationStamp;
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
  }

  @Override
  public RangeMarker getRangeGuard(int start, int end) {
    return null;
  }

  @Override
  public void startGuardedBlockChecking() {
  }

  @Override
  public void stopGuardedBlockChecking() {
  }

  @Override
  @NotNull
  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    return null;
  }

  @Override
  public void removeGuardedBlock(@NotNull RangeMarker block) {
  }

  @Override
  public RangeMarker getOffsetGuard(int offset) {
    return null;
  }

  @Override
  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
  }

  @Override
  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
  }

  @Override
  public void suppressGuardedExceptions() {
  }

  @Override
  public void unSuppressGuardedExceptions() {

  }

  @Override
  public boolean isInEventsHandling() {
    return false;
  }

  @Override
  public void clearLineModificationFlags() {
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return false;
  }

  @Override
  public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {

  }

  @Override
  public boolean isInBulkUpdate() {
    return false;
  }

  @Override
  public void setInBulkUpdate(boolean value) {
  }

  @Override
  public void setCyclicBufferSize(int bufferSize) {
  }

  @Override
  public void setText(@NotNull final CharSequence text) {
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final TextRange textRange) {
    return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  @NotNull
  public List<RangeMarker> getGuardedBlocks() {
    return Collections.emptyList();
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor) {
    return false;
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor) {
    return false;
  }

  @Override
  public int getModificationSequence() {
    return 0;
  }
}
