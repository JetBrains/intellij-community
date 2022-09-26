// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.ReplacementVariableDefinition;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.filters.ShortFilterTextProvider;
import com.intellij.util.SmartList;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class SubstitutionShortInfoHandler implements DocumentListener, EditorMouseMotionListener, CaretListener {
  private static final Key<SubstitutionShortInfoHandler> LISTENER_KEY = Key.create("sslistener.key");
  private long modificationTimeStamp;
  private final List<String> variables = new SmartList<>();
  private final Editor editor;
  private final ShortFilterTextProvider myShortFilterTextProvider;
  private final boolean myCanBeReplace;
  @Nullable private final Consumer<? super String> myCurrentVariableCallback;
  public static final Key<Configuration> CURRENT_CONFIGURATION_KEY = Key.create("SS.CurrentConfiguration");
  private final Map<String, Inlay<FilterRenderer>> inlays = new HashMap<>();

  private SubstitutionShortInfoHandler(@NotNull Editor _editor, ShortFilterTextProvider provider, boolean canBeReplace,
                                       @Nullable Consumer<? super String> currentVariableCallback) {
    editor = _editor;
    myShortFilterTextProvider = provider;
    myCanBeReplace = canBeReplace;
    myCurrentVariableCallback = currentVariableCallback;
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    handleInputFocusMovement(e.getLogicalPosition(), false);
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
      }
      return;
    }
    final String variableName = variableRange.subSequence(patternText).toString();
    final NamedScriptableDefinition variable = configuration.findVariable(variableName);
    final boolean replacementVariable =
      variable instanceof ReplacementVariableDefinition ||
      myCanBeReplace && variable == null && configuration instanceof ReplaceConfiguration;
    final String currentVariableName = replacementVariable
                                       ? variableName + ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX
                                       : variableName;
    if (caret) {
      if (myCurrentVariableCallback != null) {
        myCurrentVariableCallback.accept(currentVariableName);
      }
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
    // to handle backspace & delete (backspace strangely is not reported to the caret listener)
    handleInputFocusMovement(editor.getCaretModel().getLogicalPosition(), true);
    updateEditorInlays();
  }

  public List<String> getVariables() {
    checkModelValidity();
    return variables;
  }

  static SubstitutionShortInfoHandler retrieve(Editor editor) {
    return editor == null ? null : editor.getUserData(LISTENER_KEY);
  }

  static void install(Editor editor, ShortFilterTextProvider provider, Disposable disposable) {
    install(editor, provider, null, disposable, false);
  }

  static void install(Editor editor, ShortFilterTextProvider provider, @Nullable Consumer<? super String> currentVariableCallback, Disposable disposable, boolean replace) {
    final SubstitutionShortInfoHandler handler = new SubstitutionShortInfoHandler(editor, provider, replace, currentVariableCallback);
    editor.addEditorMouseMotionListener(handler, disposable);
    editor.getDocument().addDocumentListener(handler, disposable);
    editor.getCaretModel().addCaretListener(handler, disposable);
    editor.putUserData(LISTENER_KEY, handler);
  }

  static void updateEditorInlays(Editor editor) {
    final SubstitutionShortInfoHandler handler = retrieve(editor);
    if (handler != null) {
      handler.updateEditorInlays();
    }
  }

  void updateEditorInlays() {
    final Project project = editor.getProject();
    if (project == null) {
      return;
    }
    final String text = editor.getDocument().getText();
    final Template template = TemplateManager.getInstance(project).createTemplate("", "", text);
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
      final String labelText = myShortFilterTextProvider.getShortFilterText(variable);
      if (labelText.isEmpty()) {
        continue;
      }
      variables.remove(name);
      final Inlay<FilterRenderer> inlay = inlays.get(name);
      if (inlay == null) {
        inlays.put(name, inlayModel.addInlineElement(offset + variableNameLength, new FilterRenderer(labelText)));
      }
      else {
        final FilterRenderer renderer = inlay.getRenderer();
        renderer.setText(labelText);
        inlay.update();
      }
    }
    final NamedScriptableDefinition contextVariable = configuration.findVariable(Configuration.CONTEXT_VAR_NAME);
    final String labelText = myShortFilterTextProvider.getShortFilterText(contextVariable);
    if (!labelText.isEmpty()) {
      variables.remove(Configuration.CONTEXT_VAR_NAME);
      final Inlay<FilterRenderer> inlay = inlays.get(Configuration.CONTEXT_VAR_NAME);
      if (inlay == null) {
        inlays.put(Configuration.CONTEXT_VAR_NAME,
                   inlayModel.addBlockElement(text.length() + variableNameLength, true, false, 0,
                                              new FilterRenderer("whole template: " + labelText)));
      }
      else {
        final FilterRenderer renderer = inlay.getRenderer();
        renderer.setText("whole template: " + labelText);
        inlay.update();
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
    public int calcWidthInPixels(@NotNull Inlay inlay) {
      return getFontMetrics(inlay.getEditor()).stringWidth(myText) + 12;
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
        g.drawString(myText, r.x + 6, r.y + metrics.getAscent());
      }
    }
  }
}
