// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
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
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SubstitutionShortInfoHandler implements DocumentListener, EditorMouseMotionListener, CaretListener {
  private static final Key<SubstitutionShortInfoHandler> LISTENER_KEY = Key.create("sslistener.key");
  private static final TooltipGroup SS_INFO_TOOLTIP_GROUP = new TooltipGroup("SS_INFO_TOOLTIP_GROUP", 0);
  private long modificationTimeStamp;
  private final List<String> variables = new SmartList<>();
  private final Editor editor;
  @Nullable private final Consumer<? super String> myCurrentVariableCallback;
  public static final Key<Configuration> CURRENT_CONFIGURATION_KEY = Key.create("SS.CurrentConfiguration");
  private final Map<String, Inlay<FilterRenderer>> inlays = new HashMap<>();

  private SubstitutionShortInfoHandler(@NotNull Editor _editor, @Nullable Consumer<? super String> currentVariableCallback) {
    editor = _editor;
    myCurrentVariableCallback = currentVariableCallback;
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    final LogicalPosition position  = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());

    handleInputFocusMovement(position, false);
  }

  private void handleInputFocusMovement(LogicalPosition position, boolean caret) {
    final Configuration configuration = editor.getUserData(CURRENT_CONFIGURATION_KEY);
    if (configuration == null) {
      return;
    }
    final Document document = editor.getDocument();
    final int lineCount = document.getLineCount();
    if (position.line >= lineCount) {
      return;
    }
    final int lineStart = document.getLineStartOffset(position.line);
    final int lineEnd = document.getLineEndOffset(position.line);
    final CharSequence patternText = document.getCharsSequence().subSequence(lineStart, lineEnd);

    final TextRange variableRange = TemplateImplUtil.findVariableAtOffset(patternText, position.column);
    if (variableRange == null) {
      if (caret) {
        if (myCurrentVariableCallback != null) {
          myCurrentVariableCallback.accept(Configuration.CONTEXT_VAR_NAME);
        }
        configuration.setCurrentVariableName(Configuration.CONTEXT_VAR_NAME);
      }
      return;
    }
    final String variableName = variableRange.subSequence(patternText).toString();
    final NamedScriptableDefinition variable = configuration.findVariable(variableName);
    final String filterText =
      getShortParamString(variable, !editor.isViewer() && !variableName.equals(configuration.getCurrentVariableName()));
    final boolean replacementVariable =
      variable instanceof ReplacementVariableDefinition || variable == null && configuration instanceof ReplaceConfiguration;
    final String currentVariableName = replacementVariable
                                       ? variableName + ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX
                                       : variableName;
    if (caret) {
      if (myCurrentVariableCallback != null) {
        myCurrentVariableCallback.accept(currentVariableName);
      }
      configuration.setCurrentVariableName(currentVariableName);
    }
    if (!filterText.isEmpty()) {
      final LogicalPosition toolTipPosition =
        new LogicalPosition(position.line, variableRange.getStartOffset() +
                                           ((variableRange.getEndOffset() - variableRange.getStartOffset()) >> 1));
      showTooltip(editor, toolTipPosition, filterText);
    }
  }

  private void checkModelValidity() {
    final Document document = editor.getDocument();
    if (modificationTimeStamp != document.getModificationStamp()) {
      variables.clear();
      variables.addAll(TemplateImplUtil.parseVariableNames(document.getCharsSequence()));
      modificationTimeStamp = document.getModificationStamp();
      updateEditorInlays();
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
    updateEditorInlays();
  }

  public List<String> getVariables() {
    checkModelValidity();
    return variables;
  }

  @NotNull
  static String getShortParamString(NamedScriptableDefinition namedScriptableDefinition, boolean editLink) {
    final boolean verbose = !Registry.is("ssr.use.new.search.dialog");
    if (namedScriptableDefinition == null) {
      return verbose ? SSRBundle.message("no.constraints.specified.tooltip.message") : "";
    }

    final StringBuilder buf = new StringBuilder();

    final String linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.linkColor());
    if (namedScriptableDefinition instanceof MatchVariableConstraint) {
      final MatchVariableConstraint constraint = (MatchVariableConstraint)namedScriptableDefinition;
      final String name = constraint.getName();
      if (!Configuration.CONTEXT_VAR_NAME.equals(name)) {
        final int maxCount = constraint.getMaxCount();
        final int minCount = constraint.getMinCount();
        if (verbose || minCount != 1 || maxCount != 1) {
          append(buf, SSRBundle.message("min.occurs.tooltip.message", minCount, (maxCount == Integer.MAX_VALUE) ? "∞" : maxCount));
        }
      }
      if (constraint.isPartOfSearchResults() && verbose) {
        append(buf, SSRBundle.message("target.tooltip.message"));
      }
      if (!constraint.getRegExp().isEmpty()) {
        append(buf, SSRBundle.message("text.tooltip.message",
                                      constraint.isInvertRegExp() ? 1 : 0,
                                      StringUtil.escapeXmlEntities(constraint.getRegExp()),
                                      constraint.isWholeWordsOnly() ? 1 : 0,
                                      constraint.isWithinHierarchy() ? 1 : 0));
      }
      else if (constraint.isWithinHierarchy()) {
        append(buf, SSRBundle.message("hierarchy.tooltip.message"));
      }
      if (!StringUtil.isEmpty(constraint.getReferenceConstraint())) {
        final String text = StringUtil.escapeXmlEntities(StringUtil.unquoteString(constraint.getReferenceConstraint()));
        append(buf, SSRBundle.message("reference.target.tooltip.message", constraint.isInvertReference() ? 1 : 0, text));
      }

      if (!constraint.getNameOfExprType().isEmpty()) {
        append(buf, SSRBundle.message("exprtype.tooltip.message",
                                      constraint.isInvertExprType() ? 1 : 0,
                                      StringUtil.escapeXmlEntities(constraint.getNameOfExprType()),
                                      constraint.isExprTypeWithinHierarchy() ? 1 : 0));
      }

      constraint.getNameOfFormalArgType();
      if (!constraint.getNameOfFormalArgType().isEmpty()) {
        append(buf, SSRBundle.message("expected.type.tooltip.message",
                                      constraint.isInvertFormalType() ? 1 : 0,
                                      StringUtil.escapeXmlEntities(constraint.getNameOfFormalArgType()),
                                      constraint.isFormalArgTypeWithinHierarchy() ? 1 : 0));
      }

      if (StringUtil.isNotEmpty(constraint.getWithinConstraint())) {
        final String text = StringUtil.escapeXmlEntities(StringUtil.unquoteString(constraint.getWithinConstraint()));
        append(buf, SSRBundle.message("within.constraints.tooltip.message", constraint.isInvertWithinConstraint() ? 1 : 0, text));
      }
    }

    final String script = namedScriptableDefinition.getScriptCodeConstraint();
    if (script != null && script.length() > 2) {
      append(buf, SSRBundle.message("script.tooltip.message"));
    }

    if (buf.length() == 0 && !editLink && verbose) {
      buf.append(SSRBundle.message("no.constraints.specified.tooltip.message"));
    }
    if (editLink && !verbose && !Registry.is("ssr.use.editor.inlays.instead.of.tool.tips")) {
      if (buf.length() > 0) buf.append("<br>");
      buf.append("<a style=\"color:")
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

  private static void showTooltip(@NotNull Editor editor, LogicalPosition position, @NotNull String text) {
    if (Registry.is("ssr.use.editor.inlays.instead.of.tool.tips") && Registry.is("ssr.use.new.search.dialog")) {
      return;
    }
    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    final Point point = editor.logicalPositionToXY(position);
    point.y += editor.getLineHeight();

    final Point p = SwingUtilities.convertPoint(editor.getContentComponent(), point,
                                                editor.getComponent().getRootPane().getLayeredPane());
    final HintHint hint = new HintHint(editor, point)
      .setAwtTooltip(true)
      .setShowImmediately(true);
    TooltipController.getInstance().showTooltip(editor, p, text, visibleArea.width, false, SS_INFO_TOOLTIP_GROUP, hint);
  }

  static SubstitutionShortInfoHandler retrieve(Editor editor) {
    return editor == null ? null : editor.getUserData(LISTENER_KEY);
  }

  static void install(Editor editor, @Nullable Consumer<? super String> currentVariableCallback) {
    final SubstitutionShortInfoHandler handler = new SubstitutionShortInfoHandler(editor, currentVariableCallback);
    editor.addEditorMouseMotionListener(handler);
    editor.getDocument().addDocumentListener(handler);
    editor.getCaretModel().addCaretListener(handler);
    editor.putUserData(LISTENER_KEY, handler);
  }

  void updateEditorInlays() {
    if (!Registry.is("ssr.use.editor.inlays.instead.of.tool.tips") || !Registry.is("ssr.use.new.search.dialog")) {
      return;
    }
    final String text = editor.getDocument().getText();
    final Template template = TemplateManager.getInstance(editor.getProject()).createTemplate("", "", text);
    final int segmentsCount = template.getSegmentsCount();
    final InlayModel inlayModel = editor.getInlayModel();
    final HashSet<String> variables = new HashSet<>(inlays.keySet());
    final Configuration configuration = editor.getUserData(CURRENT_CONFIGURATION_KEY);
    if (configuration == null) return;
    int variableNameLength = 0;
    for (int i = 0; i < segmentsCount; i++) {
      final int offset = template.getSegmentOffset(i);
      final String name = template.getSegmentName(i);
      variableNameLength += name.length() + 2;
      final NamedScriptableDefinition variable = configuration.findVariable(name);
      final String labelText = getShortParamString(variable, false);
      if (labelText.isEmpty()) {
        continue;
      }
      final Inlay<FilterRenderer> inlay = inlays.get(name);
      if (inlay == null) {
        inlays.put(name, inlayModel.addInlineElement(offset + variableNameLength, new FilterRenderer(labelText)));
      }
      else {
        final FilterRenderer renderer = inlay.getRenderer();
        renderer.setText(labelText);
        inlay.updateSize();
        variables.remove(name);
      }
    }
    final Inlay<FilterRenderer> inlay = inlays.get(Configuration.CONTEXT_VAR_NAME);
    if (inlay == null) {
      final NamedScriptableDefinition variable = configuration.findVariable(Configuration.CONTEXT_VAR_NAME);
      final String labelText = getShortParamString(variable, false);
      if (!labelText.isEmpty()) {
        inlays.put(Configuration.CONTEXT_VAR_NAME,
                   inlayModel.addBlockElement(text.length() + variableNameLength, true, false, 0,
                                              new FilterRenderer("complete pattern: " + labelText)));
      }
    }
    for (String variable : variables) {
      Disposer.dispose(inlays.remove(variable));
    }
  }

  private static class FilterRenderer implements EditorCustomElementRenderer {

    private String myText;

    FilterRenderer(String text) {
      myText = text;
    }

    public void setText(String text) {
      myText = text;
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      return getFontMetrics(editor).stringWidth(myText) + 12;
    }

    private static Font getFont() {
      return UIManager.getFont("Label.font");
    }

    private static FontMetrics getFontMetrics(Editor editor) {
      return editor.getContentComponent().getFontMetrics(getFont()) ;
    }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
      final Editor editor = inlay.getEditor();
      final TextAttributes attributes = editor.getColorsScheme().getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT);
      if (attributes == null) {
        return;
      }
      final FontMetrics metrics = getFontMetrics(editor);
      final Color backgroundColor = attributes.getBackgroundColor();
      if (backgroundColor != null) {
        final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
        GraphicsUtil.paintWithAlpha(g, 0.55f);
        g.setColor(backgroundColor);
        g.fillRoundRect(r.x + 2, r.y, r.width - 4, r.height, 8, 8);
        config.restore();
      }
      final Color foregroundColor = attributes.getForegroundColor();
      if (foregroundColor != null) {
        g.setColor(foregroundColor);
        g.setFont(getFont());
        g.drawString(myText, r.x + 6, r.y + r.height - metrics.getDescent());
      }
    }
  }
}
