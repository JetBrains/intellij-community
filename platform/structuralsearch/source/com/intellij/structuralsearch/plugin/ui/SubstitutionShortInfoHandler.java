/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class SubstitutionShortInfoHandler implements DocumentListener, EditorMouseMotionListener, CaretListener {
  private long modificationTimeStamp;
  private final ArrayList<Variable> variables = new ArrayList<>();
  private final Editor editor;
  public static final Key<Configuration> CURRENT_CONFIGURATION_KEY = Key.create("SS.CurrentConfiguration");

  SubstitutionShortInfoHandler(@NotNull Editor _editor) {
    editor = _editor;
  }

  @Override
  public void mouseMoved(EditorMouseEvent e) {
    LogicalPosition position  = editor.xyToLogicalPosition( e.getMouseEvent().getPoint() );

    handleInputFocusMovement(position);
  }

  private void handleInputFocusMovement(LogicalPosition position) {
    checkModelValidity();
    String text = "";
    final int offset = editor.logicalPositionToOffset(position);
    final int length = editor.getDocument().getTextLength();
    final CharSequence elements = editor.getDocument().getCharsSequence();

    int start = offset-1;
    int end = -1;
    while(start >=0 && Character.isJavaIdentifierPart(elements.charAt(start)) && elements.charAt(start)!='$') start--;

    if (start >=0 && elements.charAt(start)=='$') {
      end = offset;

      while(end < length && Character.isJavaIdentifierPart(elements.charAt(end)) && elements.charAt(end)!='$') end++;
      if (end < length && elements.charAt(end)=='$') {
        String varname = elements.subSequence(start + 1, end).toString();
        Variable foundVar = null;

        for (final Variable var : variables) {
          if (var.getName().equals(varname)) {
            foundVar = var;
            break;
          }
        }

        if (foundVar!=null) {
          text = UIUtil.getShortParamString(editor.getUserData(CURRENT_CONFIGURATION_KEY),varname);
        }
      }
    }

    if (text.length() > 0) {
      UIUtil.showTooltip(editor, start, end + 1, text);
    }
    else {
      TooltipController.getInstance().cancelTooltips();
    }
  }

  private void checkModelValidity() {
    Document document = editor.getDocument();
    if (modificationTimeStamp != document.getModificationStamp()) {
      variables.clear();
      variables.addAll(TemplateImplUtil.parseVariables(document.getCharsSequence()).values());
      modificationTimeStamp = document.getModificationStamp();
    }
  }

  @Override
  public void mouseDragged(EditorMouseEvent e) {
  }

  @Override
  public void caretPositionChanged(CaretEvent e) {
    handleInputFocusMovement(e.getNewPosition());
  }

  public ArrayList<Variable> getVariables() {
    checkModelValidity();
    return variables;
  }
}
