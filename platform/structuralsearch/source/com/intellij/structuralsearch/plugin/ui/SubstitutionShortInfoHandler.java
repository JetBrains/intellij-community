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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.ReplacementVariableDefinition;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.ui.HintHint;
import com.intellij.util.SmartList;
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
  @Nullable private final Consumer<String> myCurrentVariableCallback;
  public static final Key<Configuration> CURRENT_CONFIGURATION_KEY = Key.create("SS.CurrentConfiguration");

  SubstitutionShortInfoHandler(@NotNull Editor _editor, @Nullable Consumer<String> currentVariableCallback) {
    editor = _editor;
    myCurrentVariableCallback = currentVariableCallback;
  }

  @Override
  public void mouseMoved(EditorMouseEvent e) {
    LogicalPosition position  = editor.xyToLogicalPosition( e.getMouseEvent().getPoint() );

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
    int end = -1;
    if (start >= 0 && elements.charAt(start) == '$') {
      end = offset;

      while (end < length && Character.isJavaIdentifierPart(elements.charAt(end)) && elements.charAt(end) != '$') end++;
      if (end < length && elements.charAt(end) == '$') {
        final String variableName = elements.subSequence(start + 1, end).toString();

        if (variables.contains(variableName)) {
          final NamedScriptableDefinition variable = configuration.findVariable(variableName);
          text = getShortParamString(variable);
          final boolean replacementVariable =
            variable instanceof ReplacementVariableDefinition || variable == null && configuration instanceof ReplaceConfiguration;
          final String currentVariableName = replacementVariable
                              ? variableName + ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX
                              : variableName;
          configuration.setCurrentVariableName(currentVariableName);
          if (myCurrentVariableCallback != null && caret) {
            myCurrentVariableCallback.accept(currentVariableName);
            caret = false;
          }
        }
      }
    }
    if (myCurrentVariableCallback != null && caret) {
      myCurrentVariableCallback.accept(Configuration.CONTEXT_VAR_NAME);
    }

    if (!text.isEmpty()) {
      showTooltip(editor, start, end + 1, text);
    }
    else {
      TooltipController.getInstance().cancelTooltips();
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
  public void mouseDragged(EditorMouseEvent e) {
  }

  @Override
  public void caretPositionChanged(CaretEvent e) {
    handleInputFocusMovement(e.getNewPosition(), true);
  }

  public List<String> getVariables() {
    checkModelValidity();
    return variables;
  }

  @NotNull
  static String getShortParamString(NamedScriptableDefinition namedScriptableDefinition) {
    if (namedScriptableDefinition == null) {
      return SSRBundle.message("no.constraints.specified.tooltip.message");
    }

    final StringBuilder buf = new StringBuilder();

    if (namedScriptableDefinition instanceof MatchVariableConstraint) {
      final MatchVariableConstraint constraint = (MatchVariableConstraint)namedScriptableDefinition;
      if (constraint.isPartOfSearchResults()) {
        append(buf, SSRBundle.message("target.tooltip.message"));
      }
      if (constraint.getRegExp() != null && !constraint.getRegExp().isEmpty()) {
        append(buf, SSRBundle.message("text.tooltip.message",
                                      constraint.isInvertRegExp() ? SSRBundle.message("not.tooltip.message") : "", constraint.getRegExp()));
      }
      if (constraint.isWithinHierarchy() || constraint.isStrictlyWithinHierarchy()) {
        append(buf, SSRBundle.message("within.hierarchy.tooltip.message"));
      }
      if (!StringUtil.isEmpty(constraint.getReferenceConstraint())) {
        final String text = StringUtil.unquoteString(constraint.getReferenceConstraint());
        append(buf, SSRBundle.message("reference.target.tooltip.message",
                                      constraint.isInvertReference() ? SSRBundle.message("not.tooltip.message") : "", text));
      }

      if (constraint.getNameOfExprType() != null && !constraint.getNameOfExprType().isEmpty()) {
        append(buf, SSRBundle.message("exprtype.tooltip.message",
                                     constraint.isInvertExprType() ? SSRBundle.message("not.tooltip.message") : "",
                                     constraint.getNameOfExprType(),
                                     constraint.isExprTypeWithinHierarchy() ? SSRBundle.message("supertype.tooltip.message") : ""));
      }

      if (constraint.getNameOfFormalArgType() != null && !constraint.getNameOfFormalArgType().isEmpty()) {
        append(buf, SSRBundle.message("expected.type.tooltip.message",
                                      constraint.isInvertFormalType() ? SSRBundle.message("not.tooltip.message") : "",
                                      constraint.getNameOfFormalArgType(),
                                      constraint.isFormalArgTypeWithinHierarchy() ? SSRBundle.message("supertype.tooltip.message") : ""));
      }

      if (StringUtil.isNotEmpty(constraint.getWithinConstraint())) {
        final String text = StringUtil.unquoteString(constraint.getWithinConstraint());
        append(buf, constraint.isInvertWithinConstraint()
                    ? SSRBundle.message("not.within.constraints.tooltip.message", text)
                    : SSRBundle.message("within.constraints.tooltip.message", text));
      }

      final String name = constraint.getName();
      if (!Configuration.CONTEXT_VAR_NAME.equals(name)) {
        if (constraint.getMinCount() == constraint.getMaxCount()) {
          append(buf, SSRBundle.message("occurs.tooltip.message", constraint.getMinCount()));
        }
        else {
          append(buf, SSRBundle.message("min.occurs.tooltip.message", constraint.getMinCount(),
                                        constraint.getMaxCount() == Integer.MAX_VALUE ?
                                        StringUtil.decapitalize(SSRBundle.message("editvarcontraints.unlimited")) :
                                        constraint.getMaxCount()));
        }
      }
    }

    final String script = namedScriptableDefinition.getScriptCodeConstraint();
    if (script != null && script.length() > 2) {
      final String str = SSRBundle.message("script.tooltip.message", StringUtil.unquoteString(script));
      append(buf, str);
    }

    if (buf.length() == 0) {
      return SSRBundle.message("no.constraints.specified.tooltip.message");
    }
    return buf.toString();
  }

  private static void append(final StringBuilder buf, final String str) {
    if (buf.length() > 0) buf.append(", ");
    buf.append(str);
  }

  private static void showTooltip(@NotNull Editor editor, final int start, int end, @NotNull String text) {
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
    final HintHint hint = new HintHint(editor, bestPoint).setAwtTooltip(true).setHighlighterType(true).setShowImmediately(true)
      .setCalloutShift(editor.getLineHeight() / 2 - 1);
    TooltipController.getInstance().showTooltip(editor, p, StringUtil.escapeXml(text), visibleArea.width, false, SS_INFO_TOOLTIP_GROUP, hint);
  }

  static SubstitutionShortInfoHandler retrieve(Editor editor) {
    return editor.getUserData(LISTENER_KEY);
  }

  static void install(Editor editor, @Nullable Consumer<String> currentVariableCallback) {
    final SubstitutionShortInfoHandler handler = new SubstitutionShortInfoHandler(editor, currentVariableCallback);
    editor.addEditorMouseMotionListener(handler);
    editor.getDocument().addDocumentListener(handler);
    editor.getCaretModel().addCaretListener(handler);
    editor.putUserData(LISTENER_KEY, handler);
  }
}
