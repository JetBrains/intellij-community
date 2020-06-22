// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.IndentsModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.util.IntPair;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IndentsModelImpl implements IndentsModel {
  private final Map<IntPair, IndentGuideDescriptor> myIndentsByLines = CollectionFactory.createSmallMemoryFootprintMap();
  private List<IndentGuideDescriptor> myIndents = new ArrayList<>();
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
}
