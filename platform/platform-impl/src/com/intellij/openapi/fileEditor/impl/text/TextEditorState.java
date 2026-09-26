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

  private final @NotNull TextEditorCaretState @NotNull [] carets;
  private final int relativeCaretPosition; // distance from primary caret to the top of editor's viewable area in pixels
  private final @NotNull TextEditorFoldingState foldingState;

  public TextEditorState() {
    this(new TextEditorCaretState[0], 0, new TextEditorFoldingState(null, null));
  }

  TextEditorState(@NotNull TextEditorCaretState @NotNull [] carets, int relativeCaretPosition) {
    this(carets, relativeCaretPosition, new TextEditorFoldingState(null, null));
  }

  private TextEditorState(@NotNull TextEditorCaretState @NotNull [] carets,
                          int relativeCaretPosition,
                          @NotNull TextEditorFoldingState foldingState) {
    this.carets = carets;
    this.relativeCaretPosition = relativeCaretPosition;
    this.foldingState = foldingState;
  }

  /**
   * Folding state is more complex than, say, line/column number, that's why its deserialization can be performed only when
   * necessary pre-requisites are met (e.g., corresponding {@link Document} is created).
   * <p/>
   * However, we can't be sure that those conditions are met on IDE startup (when editor states are read). Current method allows
   *  registering a closure within the current state object which returns folding info if possible.
   *
   * @param lazyFoldingState  delayed folding info producer
   */
  @ApiStatus.Internal
  public @NotNull TextEditorState withLazyFoldingState(@NotNull Supplier<? extends CodeFoldingState> lazyFoldingState) {
    return new TextEditorState(carets, relativeCaretPosition, new TextEditorFoldingState(null, lazyFoldingState));
  }

  @ApiStatus.Internal
  public @NotNull TextEditorState withFoldingState(@NotNull CodeFoldingState foldingState) {
    return new TextEditorState(carets, relativeCaretPosition, new TextEditorFoldingState(foldingState, null));
  }

  @ApiStatus.Internal
  public @Nullable Supplier<? extends CodeFoldingState> getLazyFoldingState() {
    return foldingState.getLazyFoldingState();
  }

  @ApiStatus.Internal
  public @Nullable CodeFoldingState getFoldingState() {
    return foldingState.getFoldingState();
  }

  TextEditorCaretState @NotNull [] getCarets() {
    return carets;
  }

  int getRelativeCaretPosition() {
    return relativeCaretPosition;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TextEditorState textEditorState)) {
      return false;
    }

    if (!Arrays.equals(carets, textEditorState.carets)) return false;
    if (relativeCaretPosition != textEditorState.relativeCaretPosition) return false;
    CodeFoldingState localFoldingState = getFoldingState();
    CodeFoldingState theirFoldingState = textEditorState.getFoldingState();
    return Objects.equals(localFoldingState, theirFoldingState);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(carets);
  }

  @Override
  public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
    if (!(otherState instanceof TextEditorState other)) return false;
    return level == FileEditorStateLevel.NAVIGATION &&
           ArrayUtil.areEqual(
             carets,
             other.carets,
             (a, b) -> Math.abs(a.line() - b.line()) < MIN_CHANGE_DISTANCE
           );
  }

  @Override
  public String toString() {
    return Arrays.toString(carets);
  }
}
