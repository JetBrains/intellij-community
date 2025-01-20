// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

public final class TextEditorState implements FileEditorState {
  private static final int MIN_CHANGE_DISTANCE = 4;

  @NotNull CaretState @NotNull [] CARETS = new CaretState[0];

  // distance from primary caret to the top of editor's viewable area in pixels
  int relativeCaretPosition;

  /**
   * State, which describes how an editor is folded.
   */
  private @Nullable CodeFoldingState foldingState;
  private Supplier<? extends CodeFoldingState> delayedFoldInfoProducer;

  /**
   * Folding state is more complex than, say, line/column number, that's why it's a deserialization can be performed only when
   * necessary pre-requisites are met (e.g., corresponding {@link Document} is created).
   * <p/>
   * However, we can't be sure that those conditions are met on IDE startup (when editor states are read). Current method allows
   *  registering a closure within the current state object which returns folding info if possible.
   *
   * @param producer  delayed folding info producer
   */
  @ApiStatus.Internal
  public void setDelayedFoldState(@NotNull Supplier<? extends CodeFoldingState> producer) {
    delayedFoldInfoProducer = producer;
  }

  @ApiStatus.Internal
  public @Nullable Supplier<? extends CodeFoldingState> getDelayedFoldState() {
    return delayedFoldInfoProducer;
  }

  @ApiStatus.Internal
  public @Nullable CodeFoldingState getFoldingState() {
    // Assuming single-thread access here.
    if (foldingState == null && delayedFoldInfoProducer != null) {
      foldingState = delayedFoldInfoProducer.get();
      if (foldingState != null) {
        delayedFoldInfoProducer = null;
      }
    }
    return foldingState;
  }

  @ApiStatus.Internal
  public void setFoldingState(@Nullable CodeFoldingState foldingState) {
    this.foldingState = foldingState;
    delayedFoldInfoProducer = null;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TextEditorState textEditorState)) {
      return false;
    }

    if (!Arrays.equals(CARETS, textEditorState.CARETS)) return false;
    if (relativeCaretPosition != textEditorState.relativeCaretPosition) return false;
    CodeFoldingState localFoldingState = getFoldingState();
    CodeFoldingState theirFoldingState = textEditorState.getFoldingState();
    return Objects.equals(localFoldingState, theirFoldingState);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(CARETS);
  }

  @Override
  public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
    if (!(otherState instanceof TextEditorState other)) return false;
    return level == FileEditorStateLevel.NAVIGATION &&
           ArrayUtil.areEqual(CARETS, other.CARETS, (a, b) -> Math.abs(a.LINE - b.LINE) < MIN_CHANGE_DISTANCE);
  }

  @Override
  public String toString() {
    return Arrays.toString(CARETS);
  }

  static final class CaretState {
    int LINE;
    int COLUMN;
    boolean LEAN_FORWARD;
    int VISUAL_COLUMN_ADJUSTMENT;
    int SELECTION_START_LINE;
    int SELECTION_START_COLUMN;
    int SELECTION_END_LINE;
    int SELECTION_END_COLUMN;

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof CaretState caretState)) {
        return false;
      }

      if (COLUMN != caretState.COLUMN) return false;
      if (LINE != caretState.LINE) return false;
      if (VISUAL_COLUMN_ADJUSTMENT != caretState.VISUAL_COLUMN_ADJUSTMENT) return false;
      if (SELECTION_START_LINE != caretState.SELECTION_START_LINE) return false;
      if (SELECTION_START_COLUMN != caretState.SELECTION_START_COLUMN) return false;
      if (SELECTION_END_LINE != caretState.SELECTION_END_LINE) return false;
      return SELECTION_END_COLUMN == caretState.SELECTION_END_COLUMN;
    }

    @Override
    public int hashCode() {
      return LINE + COLUMN;
    }

    @Override
    public String toString() {
      return "[" + LINE + "," + COLUMN + "]";
    }
  }
}
