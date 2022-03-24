// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
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
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
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
public final class XDebuggerEditorLinePainter extends EditorLinePainter {
  private static final Logger LOG = Logger.getInstance(XDebuggerEditorLinePainter.class);
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
                                                                     @NlsSafe String name,
                                                                     SimpleColoredText text,
                                                                     TextAttributes attributes,
                                                                     int lineNumber,
                                                                     Map<Variable, VariableValue> oldValues) {
    final VariableText res = new VariableText();
    res.add(new LineExtensionInfo(name, attributes));

    Variable var = new Variable(name, lineNumber);
    VariableValue variableValue = oldValues.get(var);
    if (variableValue == null) {
      variableValue = new VariableValue(text.toString(), null);
      oldValues.put(var, variableValue);
    }
    else if (!StringUtil.equals(text.toString(), variableValue.actual)) {
      variableValue.setOld(variableValue.actual);
      variableValue.actual = text.toString();
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
      LOG.error(e);
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
     return new TextAttributes(JBColor.lazy(() -> EditorColorsManager.getInstance().isDarkEditor() ? new Color(0x3d8065) : Gray._135), null, null, null, Font.ITALIC);
    }
    return attributes;
  }

  private static TextAttributes getChangedAttributes() {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES_MODIFIED);
    if (attributes == null || attributes.getForegroundColor() == null) {
      return new TextAttributes(JBColor.lazy(() -> EditorColorsManager.getInstance().isDarkEditor() ? new Color(0xa1830a) : new Color(0xca8021)), null, null, null, Font.ITALIC);
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
    // TODO: this have to be specified somewhere in XValuePresentation
    private static final List<Couple<String>> ARRAYS_WRAPPERS = List.of(Couple.of("[", "]"), Couple.of("{", "}"));
    private static final String ARRAY_DELIMITER = ", ";
    private @NlsSafe String actual;
    private @NlsSafe String old;

    VariableValue(String actual, String old) {
      this.actual = actual;
      this.old = old;
    }

    public boolean isChanged() {
      return old != null && !StringUtil.equals(actual, old) && !isCollecting(actual);
    }

    void produceChangedParts(List<? super LineExtensionInfo> result) {
      Couple<String> wrapperActual = getArrayWrapper(actual);
      if (wrapperActual != null && getArrayWrapper(old) != null) {
        List<String> actualParts = getArrayParts(actual);
        List<String> oldParts = getArrayParts(old);
        //noinspection HardCodedStringLiteral
        result.add(new LineExtensionInfo(wrapperActual.first, getNormalAttributes()));
        for (int i = 0; i < actualParts.size(); i++) {
          if (i < oldParts.size() && StringUtil.equals(actualParts.get(i), oldParts.get(i))) {
            result.add(new LineExtensionInfo(actualParts.get(i), getNormalAttributes()));
          } else {
            result.add(new LineExtensionInfo(actualParts.get(i), getChangedAttributes()));
          }
          if (i != actualParts.size() - 1) {
            result.add(new LineExtensionInfo(ARRAY_DELIMITER, getNormalAttributes()));
          }
        }
        //noinspection HardCodedStringLiteral
        result.add(new LineExtensionInfo(wrapperActual.second, getNormalAttributes()));
        return;
      }

      result.add(new LineExtensionInfo(actual, getChangedAttributes()));
    }

    void setOld(String text) {
      if (!isCollecting(text)) {
        this.old = text;
      }
    }

    //TODO:implement better detection
    static boolean isCollecting(@NotNull String text) {
      return text.contains(XDebuggerUIConstants.getCollectingDataMessage());
    }

    @Nullable
    private static Couple<String> getArrayWrapper(@Nullable String s) {
      if (s == null) {
        return null;
      }
      return ContainerUtil.find(ARRAYS_WRAPPERS, w -> s.startsWith(w.first) && s.endsWith(w.second));
    }

    private static List<String> getArrayParts(String array) {
      return StringUtil.split(array.substring(1, array.length() - 1), ARRAY_DELIMITER);
    }
  }

  private static class VariableText {
    final List<LineExtensionInfo> infos = new ArrayList<>();

    void add(LineExtensionInfo info) {
      infos.add(info);
    }
  }
}
