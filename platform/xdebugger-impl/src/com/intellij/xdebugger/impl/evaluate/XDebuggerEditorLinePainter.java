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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.NotNullProducer;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.frame.XDebugView;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueTextRendererImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class XDebuggerEditorLinePainter extends EditorLinePainter {
  public static final Key<Map<Variable, VariableValue>> CACHE = Key.create("debug.inline.variables.cache");
  @Override
  public Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project, @NotNull VirtualFile file, int lineNumber) {
    if (!Registry.is("ide.debugger.inline")) {
      return null;
    }

    final Map<Pair<VirtualFile, Integer>, Set<XValueNodeImpl>> map = project.getUserData(XVariablesView.DEBUG_VARIABLES);
    final Map<VirtualFile, Long> timestamps = project.getUserData(XVariablesView.DEBUG_VARIABLES_TIMESTAMPS);
    final Document doc = FileDocumentManager.getInstance().getDocument(file);

    if (map == null || timestamps == null || doc == null) {
      return null;
    }

    Map<Variable, VariableValue> oldValues = project.getUserData(CACHE);
    if (oldValues == null) {
      oldValues = new HashMap<Variable, VariableValue>();
      project.putUserData(CACHE, oldValues);
    }
    final Long timestamp = timestamps.get(file);
    if (timestamp == null || timestamp < doc.getModificationStamp()) {
      return null;
    }
    Set<XValueNodeImpl> values = map.get(Pair.create(file, lineNumber));
    if (values != null && !values.isEmpty()) {
      final int bpLine = getCurrentBreakPointLine(values);
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
          if (StringUtil.isEmpty(text.toString())) {
            final String type = value.getValuePresentation().getType();
            if (!StringUtil.isEmpty(type)) {
              text.append(type, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        } catch (Exception e) {
          continue;
        }
        final Color color = bpLine == lineNumber ? new JBColor(Gray._180, new Color(147, 217, 186)) : getForeground();

        final String name = value.getName();
        if (StringUtil.isEmpty(text.toString())) {
          continue;
        }
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
          variableValue.produceChangedParts(result);
        }
      }
      return result;
    }

    return null;
  }

  private static int getCurrentBreakPointLine(Set<XValueNodeImpl> values) {
    try {
      final XValueNodeImpl node = values.iterator().next();
      final XDebugSession session = XDebugView.getSession(node.getTree());
      if (session != null) {
        final XSourcePosition position = session.getCurrentPosition();
        if (position != null) {
          return position.getLine();
        }
      }
    } catch (Exception ignore){}
    return -1;
  }

  private static boolean isDarkEditor() {
    Color bg = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
    return ColorUtil.isDark(bg);
  }

  public static JBColor getForeground() {
    return new JBColor(new NotNullProducer<Color>() {
      @SuppressWarnings("UseJBColor")
      @NotNull
      @Override
      public Color produce() {
        return isDarkEditor() ? Registry.getColor("ide.debugger.inline.dark.fg.color", new Color(0x3d8065))
          : Registry.getColor("ide.debugger.inline.fg.color", new Color(0x3d8065));
      }
    });
  }

  public static JBColor getChangedForeground() {
    return new JBColor(new NotNullProducer<Color>() {
      @SuppressWarnings("UseJBColor")
      @NotNull
      @Override
      public Color produce() {
        return isDarkEditor() ? Registry.getColor("ide.debugger.inline.dark.fg.modified.color", new Color(0xa1830a))
                              : Registry.getColor("ide.debugger.inline.fg.modified.color", new Color(0xca8021));
      }
    });
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

    public void produceChangedParts(List<LineExtensionInfo> result) {
      if (isArray(actual) && isArray(old)) {
        List<String> actualParts = getArrayParts(actual);
        List<String> oldParts = getArrayParts(old);
        result.add(new LineExtensionInfo("{", getForeground(), null, null, Font.PLAIN));
        for (int i = 0; i < actualParts.size(); i++) {
          if (i < oldParts.size() && StringUtil.equals(actualParts.get(i), oldParts.get(i))) {
            result.add(new LineExtensionInfo(actualParts.get(i), getForeground(), null, null, Font.PLAIN));
          } else {
            result.add(new LineExtensionInfo(actualParts.get(i), getChangedForeground(), null, null, Font.BOLD));
          }
          if (i != actualParts.size() - 1) {
            result.add(new LineExtensionInfo(", ", getForeground(), null, null, Font.PLAIN));
          }
        }
        result.add(new LineExtensionInfo("}", getForeground(), null, null, Font.PLAIN));
        return;
      }

      result.add(new LineExtensionInfo(actual, getChangedForeground(), null, null, Font.BOLD));
    }

    private static boolean isArray(String s) {
      return s != null && s.startsWith("{") && s.endsWith("}");
    }

    private static List<String> getArrayParts(String array) {
      return StringUtil.split(array.substring(1, array.length() - 1), ", ");
    }
  }
}
