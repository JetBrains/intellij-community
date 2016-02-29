/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.containers.ObjectLongHashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.frame.XDebugView;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueTextRendererImpl;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class XDebuggerEditorLinePainter extends EditorLinePainter {
  public static final Key<Map<Variable, VariableValue>> CACHE = Key.create("debug.inline.variables.cache");
  // we want to limit number of line extensions to avoid very slow painting
  // the constant is rather random (feel free to adjust it upon getting a new information)
  private static final int LINE_EXTENSIONS_MAX_COUNT = 200;

  @Override
  public Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project, @NotNull VirtualFile file, int lineNumber) {
    if (!XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowValuesInline()) {
      return null;
    }

    XVariablesView.InlineVariablesInfo data = project.getUserData(XVariablesView.DEBUG_VARIABLES);
    final ObjectLongHashMap<VirtualFile> timestamps = project.getUserData(XVariablesView.DEBUG_VARIABLES_TIMESTAMPS);
    final Document doc = FileDocumentManager.getInstance().getDocument(file);

    if (data == null || timestamps == null || doc == null) {
      return null;
    }

    Map<Variable, VariableValue> oldValues = project.getUserData(CACHE);
    if (oldValues == null) {
      oldValues = new HashMap<Variable, VariableValue>();
      project.putUserData(CACHE, oldValues);
    }
    final Long timestamp = timestamps.get(file);
    if (timestamp == -1 || timestamp < doc.getModificationStamp()) {
      return null;
    }
    List<XValueNodeImpl> values = data.get(file, lineNumber);
    if (values != null && !values.isEmpty()) {
      XDebugSession session = XDebugView.getSession(values.iterator().next().getTree());
      final int bpLine = getCurrentBreakPointLineInFile(session, file);
      boolean isTopFrame = session instanceof XDebugSessionImpl && ((XDebugSessionImpl)session).isTopFrameSelected();
      final TextAttributes attributes = bpLine == lineNumber && isTopFrame &&
                                        ((XDebuggerManagerImpl)XDebuggerManager.getInstance(project)).isFullLineHighlighter()
                                        ? getTopFrameSelectedAttributes() : getNormalAttributes();

      ArrayList<VariableText> result = new ArrayList<VariableText>();
      for (XValueNodeImpl value : values) {
        SimpleColoredText text = new SimpleColoredText();
        XValueTextRendererImpl renderer = new XValueTextRendererImpl(text);
        final XValuePresentation presentation = value.getValuePresentation();
        if (presentation == null) continue;
        try {
          if (presentation instanceof XValueCompactPresentation && !value.getTree().isUnderRemoteDebug()) {
            ((XValueCompactPresentation)presentation).renderValue(renderer, value);
          }
          else {
            presentation.renderValue(renderer);
          }
          if (StringUtil.isEmpty(text.toString())) {
            final String type = value.getValuePresentation().getType();
            if (!StringUtil.isEmpty(type)) {
              text.append(type, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
        catch (Exception ignored) {
          continue;
        }

        final String name = value.getName();
        if (StringUtil.isEmpty(text.toString())) {
          continue;
        }
        final VariableText res = new VariableText();
        result.add(res);
        res.add(new LineExtensionInfo("  " + name + ": ", attributes));

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
            res.add(new LineExtensionInfo(s, attributes));
          }
        }
        else {
          variableValue.produceChangedParts(res.infos);
        }
      }
      final List<LineExtensionInfo> infos = new ArrayList<LineExtensionInfo>();
      for (VariableText text : result) {
        infos.addAll(text.infos);
      }
      return infos.size() > LINE_EXTENSIONS_MAX_COUNT ? infos.subList(0, LINE_EXTENSIONS_MAX_COUNT) : infos;
    }
    return null;
  }

  private static int getCurrentBreakPointLineInFile(@Nullable XDebugSession session, VirtualFile file) {
    try {
      if (session != null) {
        final XSourcePosition position = session.getCurrentPosition();
        if (position != null && position.getFile().equals(file)) {
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

  public static TextAttributes getNormalAttributes() {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES);
    if (attributes == null || attributes.getForegroundColor() == null) {
     return new TextAttributes(new JBColor(() -> isDarkEditor() ? new Color(0x3d8065) : Gray._135), null, null, null, Font.ITALIC);
    }
    return attributes;
  }

  public static TextAttributes getChangedAttributes() {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES_MODIFIED);
    if (attributes == null || attributes.getForegroundColor() == null) {
      return new TextAttributes(new JBColor(() -> isDarkEditor() ? new Color(0xa1830a) : new Color(0xca8021)), null, null, null, Font.ITALIC);
    }
    return attributes;
  }

  private static TextAttributes getTopFrameSelectedAttributes() {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
    if (attributes == null || attributes.getForegroundColor() == null) {
      //noinspection UseJBColor
      return new TextAttributes(isDarkEditor() ? new Color(255, 235, 9) : new Color(0, 255, 86), null, null, null, Font.ITALIC);
    }
    return attributes;
  }

  static class Variable {
    private final int lineNumber;
    private final String name;

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
        result.add(new LineExtensionInfo("{", getNormalAttributes()));
        for (int i = 0; i < actualParts.size(); i++) {
          if (i < oldParts.size() && StringUtil.equals(actualParts.get(i), oldParts.get(i))) {
            result.add(new LineExtensionInfo(actualParts.get(i), getNormalAttributes()));
          } else {
            result.add(new LineExtensionInfo(actualParts.get(i), getChangedAttributes()));
          }
          if (i != actualParts.size() - 1) {
            result.add(new LineExtensionInfo(", ", getNormalAttributes()));
          }
        }
        result.add(new LineExtensionInfo("}", getNormalAttributes()));
        return;
      }

      result.add(new LineExtensionInfo(actual, getChangedAttributes()));
    }

    private static boolean isArray(String s) {
      return s != null && s.startsWith("{") && s.endsWith("}");
    }

    private static List<String> getArrayParts(String array) {
      return StringUtil.split(array.substring(1, array.length() - 1), ", ");
    }
  }

  private static class VariableText {
    final List<LineExtensionInfo> infos = new ArrayList<LineExtensionInfo>();
    int length = 0;

    void add(LineExtensionInfo info) {
      infos.add(info);
      length += info.getText().length();
    }
  }
}
