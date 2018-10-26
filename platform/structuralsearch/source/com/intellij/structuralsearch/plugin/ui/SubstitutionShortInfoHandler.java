// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.ReplacementVariableDefinition;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HintHint;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class SubstitutionShortInfoHandler implements DocumentListener, EditorMouseMotionListener, CaretListener {
  private static final Key<SubstitutionShortInfoHandler> LISTENER_KEY = Key.create("sslistener.key");
  private static final TooltipGroup SS_INFO_TOOLTIP_GROUP = new TooltipGroup("SS_INFO_TOOLTIP_GROUP", 0);
  private long modificationTimeStamp;
  private final List<String> variables = new SmartList<>();
  private final Editor editor;
  @Nullable private final Consumer<? super String> myCurrentVariableCallback;
  public static final Key<Configuration> CURRENT_CONFIGURATION_KEY = Key.create("SS.CurrentConfiguration");

  SubstitutionShortInfoHandler(@NotNull Editor _editor, @Nullable Consumer<? super String> currentVariableCallback) {
    editor = _editor;
    myCurrentVariableCallback = currentVariableCallback;
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    LogicalPosition position  = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());

    handleInputFocusMovement(position, false);
  }

  private void handleInputFocusMovement(LogicalPosition position, boolean caret) {
    final Configuration configuration = editor.getUserData(CURRENT_CONFIGURATION_KEY);
    if (configuration == null) {
      return;
    }
    checkModelValidity();
    final int offset = editor.logicalPositionToOffset(position);
    final Document document = editor.getDocument();
    final int length = document.getTextLength();
    final CharSequence elements = document.getCharsSequence();

    int start = offset-1;
    while(start >=0 && Character.isJavaIdentifierPart(elements.charAt(start)) && elements.charAt(start)!='$') start--;

    String text = "";
    String variableName = null;
    int end = -1;
    if (start >= 0 && elements.charAt(start) == '$') {
      end = offset;

      while (end < length && Character.isJavaIdentifierPart(elements.charAt(end)) && elements.charAt(end) != '$') end++;
      if (end < length && elements.charAt(end) == '$') {
        variableName = elements.subSequence(start + 1, end).toString();

        if (variables.contains(variableName)) {
          final NamedScriptableDefinition variable = configuration.findVariable(variableName);
          text = getShortParamString(variable, !editor.isViewer() && !variableName.equals(configuration.getCurrentVariableName()));
          final boolean replacementVariable =
            variable instanceof ReplacementVariableDefinition || variable == null && configuration instanceof ReplaceConfiguration;
          final String currentVariableName = replacementVariable
                              ? variableName + ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX
                              : variableName;
          if (myCurrentVariableCallback != null) {
            if (caret) {
              myCurrentVariableCallback.accept(currentVariableName);
              caret = false;
            }
          }
          else {
            configuration.setCurrentVariableName(currentVariableName);
          }
        }
      }
    }
    if (myCurrentVariableCallback != null && caret) {
      myCurrentVariableCallback.accept(Configuration.CONTEXT_VAR_NAME);
    }

    if (variableName != null) {
        showTooltip(editor, start, end + 1, text, variableName);
    }
  }

  private void checkModelValidity() {
    Document document = editor.getDocument();
    if (modificationTimeStamp != document.getModificationStamp()) {
      variables.clear();
      variables.addAll(TemplateImplUtil.parseVariables(document.getCharsSequence()).keySet());
      modificationTimeStamp = document.getModificationStamp();
    }
  }

  @Override
  public void caretPositionChanged(@NotNull CaretEvent e) {
    handleInputFocusMovement(e.getNewPosition(), true);
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    if (event.getOldLength() == event.getNewLength()) return;
    // to handle backspace & delete (backspace strangely is not reported to the caret listener)
    handleInputFocusMovement(editor.getCaretModel().getLogicalPosition(), true);
  }

  public List<String> getVariables() {
    checkModelValidity();
    return variables;
  }

  @NotNull
  static String getShortParamString(NamedScriptableDefinition namedScriptableDefinition, boolean editLink) {
    final boolean newDialog = Registry.is("ssr.use.new.search.dialog");
    if (namedScriptableDefinition == null) {
      return newDialog ? "no filters" : SSRBundle.message("no.constraints.specified.tooltip.message");
    }

    final StringBuilder buf = new StringBuilder();

    final String inactiveTextColor = ColorUtil.toHtmlColor(UIUtil.getInactiveTextColor());
    final String linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.linkColor());
    if (namedScriptableDefinition instanceof MatchVariableConstraint) {
      final MatchVariableConstraint constraint = (MatchVariableConstraint)namedScriptableDefinition;
      if (constraint.isPartOfSearchResults() && !newDialog) {
        append(buf, SSRBundle.message("target.tooltip.message"));
      }
      if (constraint.getRegExp() != null && !constraint.getRegExp().isEmpty()) {
        append(buf, SSRBundle.message("text.tooltip.message",
                                      constraint.isInvertRegExp() ? 1 : 0,
                                      StringUtil.escapeXml(constraint.getRegExp()),
                                      constraint.isWholeWordsOnly() ? 1 : 0,
                                      constraint.isWithinHierarchy() ? 1 : 0,
                                      inactiveTextColor));
      }
      else if (constraint.isWithinHierarchy()) {
        append(buf, SSRBundle.message("hierarchy.tooltip.message"));
      }
      if (!StringUtil.isEmpty(constraint.getReferenceConstraint())) {
        final String text = StringUtil.escapeXml(StringUtil.unquoteString(constraint.getReferenceConstraint()));
        append(buf, SSRBundle.message("reference.target.tooltip.message", constraint.isInvertReference() ? 1 : 0, text));
      }

      if (constraint.getNameOfExprType() != null && !constraint.getNameOfExprType().isEmpty()) {
        append(buf, SSRBundle.message("exprtype.tooltip.message",
                                      constraint.isInvertExprType() ? 1 : 0,
                                      StringUtil.escapeXml(constraint.getNameOfExprType()),
                                      constraint.isExprTypeWithinHierarchy() ? 1 : 0,
                                      inactiveTextColor));
      }

      if (constraint.getNameOfFormalArgType() != null && !constraint.getNameOfFormalArgType().isEmpty()) {
        append(buf, SSRBundle.message("expected.type.tooltip.message",
                                      constraint.isInvertFormalType() ? 1 : 0,
                                      StringUtil.escapeXml(constraint.getNameOfFormalArgType()),
                                      constraint.isFormalArgTypeWithinHierarchy() ? 1 : 0,
                                      inactiveTextColor));
      }

      if (StringUtil.isNotEmpty(constraint.getWithinConstraint())) {
        final String text = StringUtil.escapeXml(StringUtil.unquoteString(constraint.getWithinConstraint()));
        append(buf, SSRBundle.message("within.constraints.tooltip.message", constraint.isInvertWithinConstraint() ? 1 : 0, text));
      }

      final String name = constraint.getName();
      if (!Configuration.CONTEXT_VAR_NAME.equals(name)) {
        final int maxCount = constraint.getMaxCount();
        final int minCount = constraint.getMinCount();
        if (!newDialog || minCount != 1 || maxCount != 1) {
          append(buf, SSRBundle.message("min.occurs.tooltip.message", minCount, (maxCount == Integer.MAX_VALUE) ? "∞" : maxCount));
        }
      }
    }

    final String script = namedScriptableDefinition.getScriptCodeConstraint();
    if (script != null && script.length() > 2) {
      final String str = SSRBundle.message("script.tooltip.message", StringUtil.unquoteString(script));
      append(buf, str);
    }

    if (buf.length() == 0 && !editLink) {
      buf.append(!newDialog ? SSRBundle.message("no.constraints.specified.tooltip.message") : "no filters");
    }
    if (editLink && newDialog) {
      buf.append(" <a style=\"color:")
        .append(linkColor)
        .append("\" href=\"#ssr_edit_filters/")
        .append(namedScriptableDefinition.getName())
        .append("\">Edit filters</a>");
    }
    return buf.toString();
  }

  private static void append(final StringBuilder buf, final String str) {
    if (buf.length() > 0) buf.append(", ");
    buf.append(str);
  }

  private static void showTooltip(@NotNull Editor editor, final int start, int end, @NotNull String text, String variableName) {
    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    final Point left = editor.logicalPositionToXY(editor.offsetToLogicalPosition(start));
    final int documentLength = editor.getDocument().getTextLength();
    if (end >= documentLength) end = documentLength;
    final Point right = editor.logicalPositionToXY(editor.offsetToLogicalPosition(end));

    final Point bestPoint = new Point(left.x + (right.x - left.x) / 2, right.y + editor.getLineHeight() / 2);

    if (visibleArea.x > bestPoint.x) {
      bestPoint.x = visibleArea.x;
    }
    else if (visibleArea.x + visibleArea.width < bestPoint.x) {
      bestPoint.x = visibleArea.x + visibleArea.width - 5;
    }

    final Point p = SwingUtilities.convertPoint(editor.getContentComponent(), bestPoint,
                                                editor.getComponent().getRootPane().getLayeredPane());
    final HintHint hint = new HintHint(editor, bestPoint)
      .setAwtTooltip(true)
      .setShowImmediately(true);
    TooltipController.getInstance().showTooltip(editor, p, text, visibleArea.width, false, SS_INFO_TOOLTIP_GROUP, hint);
  }

  static SubstitutionShortInfoHandler retrieve(Editor editor) {
    return editor.getUserData(LISTENER_KEY);
  }

  static void install(Editor editor, @Nullable Consumer<? super String> currentVariableCallback) {
    final SubstitutionShortInfoHandler handler = new SubstitutionShortInfoHandler(editor, currentVariableCallback);
    editor.addEditorMouseMotionListener(handler);
    editor.getDocument().addDocumentListener(handler);
    editor.getCaretModel().addCaretListener(handler);
    editor.putUserData(LISTENER_KEY, handler);
  }
}
