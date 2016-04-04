/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Vladimir Kondratyev
 */
public final class TextEditorState implements FileEditorState {

  public CaretState[] CARETS;

  public int RELATIVE_CARET_POSITION; // distance from primary caret to the top of editor's viewable area in pixels

  /**
   * State which describes how editor is folded.
   * This field can be <code>null</code>.
   */
  private           CodeFoldingState           myFoldingState;
  @Nullable private Producer<CodeFoldingState> myDelayedFoldInfoProducer;

  private static final int MIN_CHANGE_DISTANCE = 4;

  public TextEditorState() {
  }

  /**
   * Folding state is more complex than, say, line/column number, that's why it's deserialization can be performed only when
   * necessary pre-requisites are met (e.g. corresponding {@link Document} is created).
   * <p/>
   * However, we can't be sure that those conditions are met on IDE startup (when editor states are read). Current method allows
   * to register a closure within the current state object which returns folding info if possible.
   *
   * @param producer  delayed folding info producer
   */
  public void setDelayedFoldState(@NotNull Producer<CodeFoldingState> producer) {
    myDelayedFoldInfoProducer = producer;
  }

  @Nullable
  public CodeFoldingState getFoldingState() {
    // Assuming single-thread access here.
    if (myFoldingState == null && myDelayedFoldInfoProducer != null) {
      myFoldingState = myDelayedFoldInfoProducer.produce();
      if (myFoldingState != null) {
        myDelayedFoldInfoProducer = null;
      }
    }
    return myFoldingState;
  }

  public void setFoldingState(@Nullable CodeFoldingState foldingState) {
    myFoldingState = foldingState;
    myDelayedFoldInfoProducer = null;
  }

  public boolean equals(Object o) {
    if (!(o instanceof TextEditorState)) {
      return false;
    }

    final TextEditorState textEditorState = (TextEditorState)o;

    if (!Arrays.equals(CARETS, textEditorState.CARETS)) return false;
    if (RELATIVE_CARET_POSITION != textEditorState.RELATIVE_CARET_POSITION) return false;
    CodeFoldingState localFoldingState = getFoldingState();
    CodeFoldingState theirFoldingState = textEditorState.getFoldingState();
    if (localFoldingState == null ? theirFoldingState != null : !localFoldingState.equals(theirFoldingState)) return false;

    return true;
  }

  public int hashCode() {
    int result = 0;
    if (CARETS != null) {
      for (CaretState caretState : CARETS) {
        if (caretState != null) {
          result += caretState.hashCode();
        }
      }
    }
    return result;
  }

  @Override
  public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
    if (!(otherState instanceof TextEditorState)) return false;
    TextEditorState other = (TextEditorState)otherState;
    return level == FileEditorStateLevel.NAVIGATION
           && CARETS != null && CARETS.length == 1
           && other.CARETS != null && other.CARETS.length == 1
           && Math.abs(CARETS[0].LINE - other.CARETS[0].LINE) < MIN_CHANGE_DISTANCE;
  }

  public String toString() {
    return Arrays.toString(CARETS);
  }

  public static class CaretState {
    public int   LINE;
    public int   COLUMN;
    public int   SELECTION_START_LINE;
    public int   SELECTION_START_COLUMN;
    public int   SELECTION_END_LINE;
    public int   SELECTION_END_COLUMN;

    public boolean equals(Object o) {
      if (!(o instanceof CaretState)) {
        return false;
      }

      final CaretState caretState = (CaretState)o;

      if (COLUMN != caretState.COLUMN) return false;
      if (LINE != caretState.LINE) return false;
      if (SELECTION_START_LINE != caretState.SELECTION_START_LINE) return false;
      if (SELECTION_START_COLUMN != caretState.SELECTION_START_COLUMN) return false;
      if (SELECTION_END_LINE != caretState.SELECTION_END_LINE) return false;
      if (SELECTION_END_COLUMN != caretState.SELECTION_END_COLUMN) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return LINE + COLUMN;
    }

    public String toString() {
      return "[" + LINE + "," + COLUMN + "]";
    }
  }
}
