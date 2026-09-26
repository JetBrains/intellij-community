// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;


record TextEditorCaretState(
  int line,
  int column,
  boolean leanForward,
  int visualColumnAdjustment,
  int selectionStartLine,
  int selectionStartColumn,
  int selectionEndLine,
  int selectionEndColumn
) {

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TextEditorCaretState caretState)) {
      return false;
    }

    if (line() != caretState.line()) return false;
    if (column() != caretState.column()) return false;
    // leanForward is excluded intentionally
    if (visualColumnAdjustment() != caretState.visualColumnAdjustment()) return false;
    if (selectionStartLine() != caretState.selectionStartLine()) return false;
    if (selectionStartColumn() != caretState.selectionStartColumn()) return false;
    if (selectionEndLine() != caretState.selectionEndLine()) return false;
    return selectionEndColumn() == caretState.selectionEndColumn();
  }

  @Override
  public int hashCode() {
    return line() + column();
  }

  @Override
  public String toString() {
    return "[" + line + "," + column + "]";
  }
}
