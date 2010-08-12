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
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.impl.EmptyMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.LocalTimeCounter;
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

  public String getText() {
    return myText.toString();
  }

  @Override
  public String getText(TextRange range) {
    return myText.substring(range.getStartOffset(), range.getEndOffset());
  }

  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    myText = new StringBuffer();
    myText.append(chars);
    myModStamp = newModificationStamp;
  }

  public int getListenersCount() {
    return 0;
  }

  public CharSequence textToCharArray() {
    return getText();
  }

  @NotNull
  public char[] getChars() {
    return getText().toCharArray();
  }

  @NotNull
  public CharSequence getCharsSequence() {
    return getText();
  }

  public int getTextLength() {
    return myText.length();
  }

  public int getLineCount() {
    return 1;
  }

  public int getLineNumber(int offset) {
    return 0;
  }

  public int getLineStartOffset(int line) {
    return 0;
  }

  public int getLineEndOffset(int line) {
    return myText.length();
  }

  public void insertString(int offset, @NotNull CharSequence s) {
    myText.insert(offset, s);
  }

  public void deleteString(int startOffset, int endOffset) {
    myText.delete(startOffset, endOffset);
  }

  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    myText.replace(startOffset, endOffset, s.toString());
    myModStamp = LocalTimeCounter.currentTime();
  }

  public boolean isWritable() {
    return false;
  }

  public long getModificationStamp() {
    return myModStamp;
  }

  public void fireReadOnlyModificationAttempt() {
  }

  public void addDocumentListener(@NotNull DocumentListener listener) {
  }

  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
  }

  public void removeDocumentListener(@NotNull DocumentListener listener) {
  }

  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    return null;
  }

  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    return null;
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    return null;
  }

  @NotNull
  public MarkupModel getMarkupModel(Project project) {
    return new EmptyMarkupModel(this);
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getUserData(@NotNull Key<T> key) {
    return (T)myUserData.get(key);
  }

  @SuppressWarnings({"unchecked"})
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    myUserData.put(key, value);
  }

  public void stripTrailingSpaces(boolean inChangedLinesOnly) {
  }

  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
  }

  public int getLineSeparatorLength(int line) {
    return 0;
  }

  @NotNull
  public LineIterator createLineIterator() {
    return null;
  }

  public void setModificationStamp(long modificationStamp) {
    myModStamp = modificationStamp;
  }

  public void setReadOnly(boolean isReadOnly) {
  }

  public RangeMarker getRangeGuard(int start, int end) {
    return null;
  }

  public void startGuardedBlockChecking() {
  }

  public void stopGuardedBlockChecking() {
  }

  @NotNull
  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    return null;
  }

  public void removeGuardedBlock(@NotNull RangeMarker block) {
  }

  public RangeMarker getOffsetGuard(int offset) {
    return null;
  }

  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
  }

  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
  }

  public void suppressGuardedExceptions() {
  }

  public void unSuppressGuardedExceptions() {

  }

  public boolean isInEventsHandling() {
    return false;
  }

  public void clearLineModificationFlags() {
  }

  public void removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {}

  public void addRangeMarker(@NotNull RangeMarkerEx rangeMarker) {}

  public boolean isInBulkUpdate() {
    return false;
  }

  public void setInBulkUpdate(boolean value) {
  }

  public void setCyclicBufferSize(int bufferSize) {
  }

  public void setText(@NotNull final CharSequence text) {
  }

  @NotNull
  public RangeMarker createRangeMarker(@NotNull final TextRange textRange) {
    return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @NotNull
  public List<RangeMarker> getGuardedBlocks() {
    return Collections.emptyList();
  }
}
