/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.inline.XDebuggerInlayUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueTextRendererImpl;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

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
    if (LightEdit.owns(project)) return null;
    if (!XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowValuesInline()) {
      return null;
    }

    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) {
      return null;
    }

    XVariablesView.InlineVariablesInfo data = XVariablesView.InlineVariablesInfo.get(session);
    final Document doc = FileDocumentManager.getInstance().getDocument(file);

    if (data == null || doc == null) {
      return null;
    }

    Map<Variable, VariableValue> oldValues = getOldValues(project);
    List<XValueNodeImpl> values = data.get(file, lineNumber, doc.getModificationStamp());
    if (values != null && !values.isEmpty()) {
      final TextAttributes attributes = getAttributes(lineNumber, file, session);

      ArrayList<VariableText> result = new ArrayList<>();
      for (XValueNodeImpl value : values) {
        SimpleColoredText text = createPresentation(value);
        if (text == null) continue;

        final String name = value.getName();
        if (StringUtil.isEmpty(text.toString())) {
          continue;
        }

        final VariableText res = computeVariablePresentationWithChanges(value, name, text, attributes, lineNumber, oldValues);
        result.add(res);
      }
      final List<LineExtensionInfo> infos = new ArrayList<>();
      for (VariableText text : result) {
        LineExtensionInfo varNameInfo = text.infos.get(0);
        LineExtensionInfo wrappedName =
          new LineExtensionInfo("  " + varNameInfo.getText() + XDebuggerInlayUtil.INLINE_HINTS_DELIMETER + " ",
                                varNameInfo.getColor(),
                                varNameInfo.getEffectType(),
                                varNameInfo.getEffectColor(),
                                varNameInfo.getFontType());
        List<LineExtensionInfo> value = text.infos.subList(1, text.infos.size());
        infos.add(wrappedName);
        infos.addAll(value);
      }
      return ContainerUtil.getFirstItems(infos, LINE_EXTENSIONS_MAX_COUNT);
    }
    return null;
  }

  public static SimpleColoredText computeVariablePresentationWithChanges(XValueNodeImpl value,
                                                                         String name,
                                                                         SimpleColoredText text,
                                                                         TextAttributes attributes,
                                                                         int lineNumber,
                                                                         Project project) {
    Map<Variable, VariableValue> oldValues = getOldValues(project);
    VariableText variableText = computeVariablePresentationWithChanges(value, name, text, attributes, lineNumber, oldValues);

    SimpleColoredText coloredText = new SimpleColoredText();
    for (LineExtensionInfo info : variableText.infos) {
      TextAttributes textAttributes = new TextAttributes(info.getColor(), info.getBgColor(), info.getEffectColor(),info.getEffectType(), info.getFontType());
      coloredText.append(info.getText(), SimpleTextAttributes.fromTextAttributes(textAttributes));
    }
    return coloredText;
  }

  @NotNull
  private static VariableText computeVariablePresentationWithChanges(XValueNodeImpl value,
                                                                     String name,
                                                                     SimpleColoredText text,
                                                                     TextAttributes attributes,
                                                                     int lineNumber,
                                                                     Map<Variable, VariableValue> oldValues) {
    final VariableText res = new VariableText();
    res.add(new LineExtensionInfo(name, attributes));

    Variable var = new Variable(name, lineNumber);
    VariableValue variableValue = oldValues.computeIfAbsent(var, k -> new VariableValue(text.toString(), null, value.hashCode()));
    if (variableValue.valueNodeHashCode != value.hashCode()) {
      variableValue.old = variableValue.actual;
      variableValue.actual = text.toString();
      variableValue.valueNodeHashCode = value.hashCode();
    }

    if (!variableValue.isChanged()) {
      ArrayList<String> texts = text.getTexts();
      for (int i = 0; i < texts.size(); i++) {
        String s = texts.get(i);
        TextAttributes attr = Registry.is("debugger.show.values.colorful")
                              ? text.getAttributes().get(i).toTextAttributes()
                              : attributes;
        res.add(new LineExtensionInfo(s, attr));
      }
    }
    else {
      variableValue.produceChangedParts(res.infos);
    }
    return res;
  }

  @NotNull
  public static Map<Variable, VariableValue> getOldValues(@NotNull Project project) {
    Map<Variable, VariableValue> oldValues = project.getUserData(CACHE);
    if (oldValues == null) {
      oldValues = new HashMap<>();
      project.putUserData(CACHE, oldValues);
    }
    return oldValues;
  }

  @NotNull
  public static TextAttributes getAttributes(int lineNumber, @NotNull VirtualFile file, XDebugSession session) {
    final int bpLine = getCurrentBreakPointLineInFile(session, file);
    boolean isTopFrame = session instanceof XDebugSessionImpl && ((XDebugSessionImpl)session).isTopFrameSelected();
    return bpLine == lineNumber
           && isTopFrame
           && ((XDebuggerManagerImpl)XDebuggerManager.getInstance(session.getProject())).isFullLineHighlighter()
           ? getTopFrameSelectedAttributes() : getNormalAttributes();
  }

  @Nullable
  public static SimpleColoredText createPresentation(@NotNull XValueNodeImpl value) {
    SimpleColoredText text = new SimpleColoredText();
    XValueTextRendererImpl renderer = new XValueTextRendererImpl(text);
    final XValuePresentation presentation = value.getValuePresentation();
    if (presentation == null) return null;
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
    catch (Exception e) {
      return null;
    }
    return text;
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

  private static TextAttributes getNormalAttributes() {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES);
    if (attributes == null || attributes.getForegroundColor() == null) {
     return new TextAttributes(new JBColor(() -> EditorColorsManager.getInstance().isDarkEditor() ? new Color(0x3d8065) : Gray._135), null, null, null, Font.ITALIC);
    }
    return attributes;
  }

  private static TextAttributes getChangedAttributes() {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES_MODIFIED);
    if (attributes == null || attributes.getForegroundColor() == null) {
      return new TextAttributes(new JBColor(() -> EditorColorsManager.getInstance().isDarkEditor() ? new Color(0xa1830a) : new Color(0xca8021)), null, null, null, Font.ITALIC);
    }
    return attributes;
  }

  private static TextAttributes getTopFrameSelectedAttributes() {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
    if (attributes == null || attributes.getForegroundColor() == null) {
      //noinspection UseJBColor
      return new TextAttributes(EditorColorsManager.getInstance().isDarkEditor() ? new Color(255, 235, 9) : new Color(0, 255, 86), null, null, null, Font.ITALIC);
    }
    return attributes;
  }

  static class Variable {
    private final int lineNumber;
    private final String name;

    Variable(String name, int lineNumber) {
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
      return Objects.hash(lineNumber, name);
    }
  }

  static class VariableValue {
   private String actual;
   private String old;
   private int valueNodeHashCode;

    VariableValue(String actual, String old, int valueNodeHashCode) {
      this.actual = actual;
      this.old = old;
      this.valueNodeHashCode = valueNodeHashCode;
    }

    public boolean isChanged() {
      return old != null && !StringUtil.equals(actual, old);
    }

    void produceChangedParts(List<? super LineExtensionInfo> result) {
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
    final List<LineExtensionInfo> infos = new ArrayList<>();

    void add(LineExtensionInfo info) {
      infos.add(info);
    }
  }
}
