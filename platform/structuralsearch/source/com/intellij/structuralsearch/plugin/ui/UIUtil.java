package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.StructuralReplaceAction;
import com.intellij.structuralsearch.plugin.StructuralSearchAction;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.ui.HintHint;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Maxim.Mossienko
 * Date: Apr 21, 2004
 * Time: 7:50:48 PM
 */
public class UIUtil {
  static Key<SubstitutionShortInfoHandler> LISTENER_KEY = Key.create("sslistener.key");
  private static final String MODIFY_EDITOR_CONTENT = SSRBundle.message("modify.editor.content.command.name");
  private static final TooltipGroup SS_INFO_TOOLTIP_GROUP = new TooltipGroup("SS_INFO_TOOLTIP_GROUP", 0);
  @NonNls private static final String SS_GROUP = "structuralsearchgroup";

  @NotNull
  public static Editor createEditor(Document doc, final Project project, boolean editable, @Nullable TemplateContextType contextType) {
    return createEditor(doc, project, editable, false, contextType);
  }

  @NotNull
  public static Editor createEditor(@NotNull Document doc,
                                    final Project project,
                                    boolean editable,
                                    boolean addToolTipForVariableHandler,
                                    @Nullable TemplateContextType contextType) {
    final Editor editor =
        editable ? EditorFactory.getInstance().createEditor(doc, project) : EditorFactory.getInstance().createViewer(doc, project);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);

    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);
    if (!editable) {
      final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      Color c = globalScheme.getColor(EditorColors.READONLY_BACKGROUND_COLOR);

      if (c == null) {
        c = globalScheme.getDefaultBackground();
      }

      ((EditorEx)editor).setBackgroundColor(c);
    }
    else {
      ((EditorEx)editor).setEmbeddedIntoDialogWrapper(true);
    }
  
    TemplateEditorUtil.setHighlighter(editor, contextType);

    if (addToolTipForVariableHandler) {
      SubstitutionShortInfoHandler handler = new SubstitutionShortInfoHandler(editor);
      editor.addEditorMouseMotionListener(handler);
      editor.getDocument().addDocumentListener(handler);
      editor.getCaretModel().addCaretListener(handler);
      editor.putUserData(LISTENER_KEY, handler);
    }

    return editor;
  }

  public static JComponent createOptionLine(JComponent[] options) {
    JPanel tmp = new JPanel();

    tmp.setLayout(new BoxLayout(tmp, BoxLayout.X_AXIS));
    for (int i = 0; i < options.length; i++) {
      if (i != 0) {
        tmp.add(Box.createHorizontalStrut(com.intellij.util.ui.UIUtil.DEFAULT_HGAP));
      }
      tmp.add(options[i]);
    }
    tmp.add(Box.createHorizontalGlue());

    return tmp;
  }

  public static JComponent createOptionLine(JComponent option) {
    return createOptionLine(new JComponent[]{option});
  }

  public static void setContent(final Editor editor, String val, final int from, final int end, final Project project) {
    final String value = val != null ? val : "";

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            editor.getDocument().replaceString(from, (end == -1) ? editor.getDocument().getTextLength() : end, value);
          }
        });
      }
    }, MODIFY_EDITOR_CONTENT, SS_GROUP);
  }

  static String getShortParamString(Configuration config, String varname) {
    if (config == null) return "";
    final MatchOptions options = config.getMatchOptions();


    final MatchVariableConstraint constraint = options == null ? null : options.getVariableConstraint(varname);
    NamedScriptableDefinition namedScriptableDefinition = constraint;

    final ReplacementVariableDefinition replacementVariableDefinition =
      config instanceof ReplaceConfiguration ? ((ReplaceConfiguration)config).getOptions().getVariableDefinition(varname) : null;
    if (replacementVariableDefinition != null) namedScriptableDefinition = replacementVariableDefinition;

    if (constraint == null && replacementVariableDefinition == null) {
      return SSRBundle.message("no.constraints.specified.tooltip.message");
    }

    final StringBuilder buf = new StringBuilder();

    if (constraint != null) {
      if (constraint.isPartOfSearchResults()) {
        append(buf, SSRBundle.message("target.tooltip.message"));
      }
      if (constraint.getRegExp() != null && constraint.getRegExp().length() > 0) {
        append(buf, SSRBundle.message("text.tooltip.message", constraint.isInvertRegExp() ? SSRBundle.message("not.tooltip.message") : "",
                                     constraint.getRegExp(),
                                     constraint.isWithinHierarchy() || constraint.isStrictlyWithinHierarchy() ?
                                     SSRBundle.message("within.hierarchy.tooltip.message") : ""));
      }

      if (constraint.getNameOfExprType() != null && constraint.getNameOfExprType().length() > 0) {
        append(buf, SSRBundle.message("exprtype.tooltip.message",
                                     constraint.isInvertExprType() ? SSRBundle.message("not.tooltip.message") : "",
                                     constraint.getNameOfExprType(),
                                     constraint.isExprTypeWithinHierarchy() ? SSRBundle.message("within.hierarchy.tooltip.message") : ""));
      }

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

    final String script = namedScriptableDefinition.getScriptCodeConstraint();
    if (script != null && script.length() > 2) {
      final String str = SSRBundle.message("script.tooltip.message", StringUtil.stripQuotesAroundValue(script));
      append(buf, str);
    }

    return buf.toString();
  }

  private static void append(final StringBuilder buf, final String str) {
    if (buf.length() > 0) buf.append(", ");
    buf.append(str);
  }

  public static void invokeAction(Configuration config, SearchContext context) {
    if (config instanceof SearchConfiguration) {
      StructuralSearchAction.triggerAction(config, context);
    }
    else {
      StructuralReplaceAction.triggerAction(config, context);
    }
  }

  static void showTooltip(@NotNull Editor editor, final int start, int end, @NotNull String text) {
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
    final HintHint hint = new HintHint(editor, bestPoint).setAwtTooltip(true).setHighlighterType(true)
      .setCalloutShift(editor.getLineHeight() / 2 - 1);
    TooltipController.getInstance().showTooltip(editor, p, text, visibleArea.width, false, SS_INFO_TOOLTIP_GROUP, hint);
  }

  public static void updateHighlighter(Editor editor, StructuralSearchProfile profile) {
    TemplateEditorUtil.setHighlighter(editor, profile.getTemplateContextType());
  }
}
