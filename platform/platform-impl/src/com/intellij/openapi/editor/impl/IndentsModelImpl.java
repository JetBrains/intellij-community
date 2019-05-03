// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.IndentsModel;
import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndentsModelImpl implements IndentsModel {

  private final Map<IntPair, IndentGuideDescriptor> myIndentsByLines = new HashMap<>();
  private       List<IndentGuideDescriptor>         myIndents        = new ArrayList<>();
  @NotNull private final EditorImpl myEditor;

  public IndentsModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
  }

  @NotNull
  public List<IndentGuideDescriptor> getIndents() {
    return myIndents;
  }

  @Override
  public IndentGuideDescriptor getCaretIndentGuide() {
    final LogicalPosition pos = myEditor.getCaretModel().getLogicalPosition();
    final int column = pos.column;
    final int line = pos.line;

    if (column > 0) {
      for (IndentGuideDescriptor indent : myIndents) {
        if (column == indent.indentLevel && line >= indent.startLine && line < indent.endLine) {
          return indent;
        }
      }
    }
    return null;
  }

  @Override
  public IndentGuideDescriptor getDescriptor(int startLine, int endLine) {
    return myIndentsByLines.get(new IntPair(startLine, endLine));
  }

  @Override
  public void assumeIndents(@NotNull List<IndentGuideDescriptor> descriptors) {
    myIndents = descriptors;
    myIndentsByLines.clear();
    for (IndentGuideDescriptor descriptor : myIndents) {
      myIndentsByLines.put(new IntPair(descriptor.startLine, descriptor.endLine), descriptor);
    }
  }

  private static class IntPair {

    private final int start;
    private final int end;

    IntPair(int start, int end) {
      this.start = start;
      this.end = end;
    }

    @Override
    public int hashCode() {
      return 31 * start + end;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      IntPair that = (IntPair)o;
      return start == that.start && end == that.end;
    }

    @Override
    public String toString() {
      return "start=" + start + ", end=" + end;
    }
  }
}
