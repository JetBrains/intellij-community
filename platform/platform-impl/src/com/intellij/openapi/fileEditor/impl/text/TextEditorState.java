// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  CaretState[] CARETS;

  int RELATIVE_CARET_POSITION; // distance from primary caret to the top of editor's viewable area in pixels

  /**
   * State which describes how editor is folded.
   * This field can be {@code null}.
   */
  private CodeFoldingState myFoldingState;
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
  void setDelayedFoldState(@NotNull Producer<CodeFoldingState> producer) {
    myDelayedFoldInfoProducer = producer;
  }

  @Nullable
  Producer<CodeFoldingState> getDelayedFoldState() {
    return myDelayedFoldInfoProducer;
  }

  @Nullable
  CodeFoldingState getFoldingState() {
    // Assuming single-thread access here.
    if (myFoldingState == null && myDelayedFoldInfoProducer != null) {
      myFoldingState = myDelayedFoldInfoProducer.produce();
      if (myFoldingState != null) {
        myDelayedFoldInfoProducer = null;
      }
    }
    return myFoldingState;
  }

  void setFoldingState(@Nullable CodeFoldingState foldingState) {
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

  static class CaretState {
    int   LINE;
    int   COLUMN;
    boolean LEAN_FORWARD;
    int   VISUAL_COLUMN_ADJUSTMENT;
    int   SELECTION_START_LINE;
    int   SELECTION_START_COLUMN;
    int   SELECTION_END_LINE;
    int   SELECTION_END_COLUMN;

    public boolean equals(Object o) {
      if (!(o instanceof CaretState)) {
        return false;
      }

      final CaretState caretState = (CaretState)o;

      if (COLUMN != caretState.COLUMN) return false;
      if (LINE != caretState.LINE) return false;
      if (LEAN_FORWARD != caretState.LEAN_FORWARD) return false;
      if (VISUAL_COLUMN_ADJUSTMENT != caretState.VISUAL_COLUMN_ADJUSTMENT) return false;
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
