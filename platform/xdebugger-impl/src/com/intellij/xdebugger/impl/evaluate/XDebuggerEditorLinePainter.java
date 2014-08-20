/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredText;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueTextRendererImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class XDebuggerEditorLinePainter extends EditorLinePainter {
  public static final Key<Map<Variable, VariableValue>> CACHE = Key.create("debug.frame");
  @Override
  public Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project, @NotNull VirtualFile file, int lineNumber) {
    if (!Registry.is("ide.debugger.inline")) {
      return null;
    }

    Map<Pair<VirtualFile, Integer>, Set<XValueNodeImpl>> map = project.getUserData(XVariablesView.DEBUG_VARIABLES);
    Map<Variable, VariableValue> oldValues = project.getUserData(CACHE);
    if (oldValues == null) {
      oldValues = new HashMap<Variable, VariableValue>();
      project.putUserData(CACHE, oldValues);
    }
    if (map != null) {
      Set<XValueNodeImpl> values = map.get(Pair.create(file, lineNumber));
      if (values != null && !values.isEmpty()) {
        ArrayList<LineExtensionInfo> result = new ArrayList<LineExtensionInfo>();
        for (XValueNodeImpl value : values) {
          SimpleColoredText text = new SimpleColoredText();
          XValueTextRendererImpl renderer = new XValueTextRendererImpl(text);
          final XValuePresentation presentation = value.getValuePresentation();
          if (presentation == null) continue;
          try {
            if (presentation instanceof XValueCompactPresentation) {
              ((XValueCompactPresentation)presentation).renderValue(renderer, value);
            } else {
              presentation.renderValue(renderer);
            }
          } catch (Exception e) {
            continue;
          }
          final Color color = new JBColor(new Color(61, 128, 101), new Color(61, 128, 101));
          final String name = value.getName();
          result.add(new LineExtensionInfo("  " + name + ": ", color, null, null, Font.PLAIN));

          Variable var = new Variable(name, lineNumber);
          VariableValue variableValue = oldValues.get(var);
          if (variableValue == null) {
            variableValue = new VariableValue(text.toString(), null, value.hashCode());
            oldValues.put(var, variableValue);
          }
          if (variableValue.valueNodeHashCode != value.hashCode()) {
            variableValue.old = variableValue.actual;
            variableValue.actual = text.toString();
            variableValue.valueNodeHashCode = value.hashCode();
          }

          if (!variableValue.isChanged()) {
            for (String s : text.getTexts()) {
              result.add(new LineExtensionInfo(s, color, null, null, Font.PLAIN));
            }
          } else {
            for (String s : text.getTexts()) {
              result.add(new LineExtensionInfo(s, new JBColor(new Color(202, 128, 33), new Color(116, 114, 4)), null, null, Font.BOLD));
            }
          }
        }
        return result;
      }
    }

    return null;
  }

  static class Variable {
    private int lineNumber;
    private String name;

    public Variable(String name, int lineNumber) {
      this.lineNumber = lineNumber;
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Variable variable = (Variable)o;

      if (lineNumber != variable.lineNumber) return false;
      if (!name.equals(variable.name)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = lineNumber;
      result = 31 * result + name.hashCode();
      return result;
    }
  }

  static class VariableValue {
   private String actual;
   private String old;
   private int valueNodeHashCode;

    public VariableValue(String actual, String old, int valueNodeHashCode) {
      this.actual = actual;
      this.old = old;
      this.valueNodeHashCode = valueNodeHashCode;
    }

    public boolean isChanged() {
      return old != null && !StringUtil.equals(actual, old);
    }
  }
}
