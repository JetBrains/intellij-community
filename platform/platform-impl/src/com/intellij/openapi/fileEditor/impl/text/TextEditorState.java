/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/**
 * @author Vladimir Kondratyev
 */
public final class TextEditorState implements FileEditorState {

  public int   LINE;
  public int   COLUMN;
  public float VERTICAL_SCROLL_PROPORTION;
  public int   SELECTION_START;
  public int   SELECTION_END;
  public int   VERTICAL_SCROLL_OFFSET;
  public int   MAX_VERTICAL_SCROLL_OFFSET;

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

    if (COLUMN != textEditorState.COLUMN) return false;
    if (LINE != textEditorState.LINE) return false;
    if (VERTICAL_SCROLL_PROPORTION != textEditorState.VERTICAL_SCROLL_PROPORTION) return false;
    if (SELECTION_START != textEditorState.SELECTION_START) return false;
    if (SELECTION_END != textEditorState.SELECTION_END) return false;
    CodeFoldingState localFoldingState = getFoldingState();
    CodeFoldingState theirFoldingState = textEditorState.getFoldingState();
    if (localFoldingState == null ? theirFoldingState != null : !localFoldingState.equals(theirFoldingState)) return false;

    return true;
  }

  public int hashCode() {
    return LINE + COLUMN;
  }

  @Override
  public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
    if (!(otherState instanceof TextEditorState)) return false;
    TextEditorState other = (TextEditorState)otherState;
    return level == FileEditorStateLevel.NAVIGATION && Math.abs(LINE - other.LINE) < MIN_CHANGE_DISTANCE;
  }

  public String toString() {
    return "[" + LINE + "," + COLUMN + "]";
  }
}
