/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.IndentsModel;
import com.intellij.openapi.editor.LogicalPosition;

import java.util.ArrayList;
import java.util.List;

public class IndentsModelImpl implements IndentsModel {
  private EditorImpl myEditor;
  private List<IndentGuideDescriptor> myIndents = new ArrayList<IndentGuideDescriptor>();

  public IndentsModelImpl(EditorImpl editor) {
    myEditor = editor;
  }

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

  public void assumeIndents(List<IndentGuideDescriptor> descriptors) {
    myIndents = descriptors;
  }
}
