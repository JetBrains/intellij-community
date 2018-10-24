// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.notification.NotificationGroup;
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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.plugin.StructuralReplaceAction;
import com.intellij.structuralsearch.plugin.StructuralSearchAction;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TooltipWithClickableLinks;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Maxim.Mossienko
 */
public class UIUtil {
  private static final String MODIFY_EDITOR_CONTENT = SSRBundle.message("modify.editor.content.command.name");
  @NonNls private static final String SS_GROUP = "structuralsearchgroup";

  public static final NotificationGroup SSR_NOTIFICATION_GROUP =
    NotificationGroup.toolWindowGroup(SSRBundle.message("structural.search.title"), ToolWindowId.FIND);

  @NonNls public static final String TEXT = "TEXT";
  @NonNls public static final String TEXT_HIERARCHY = "TEXT HIERARCHY";
  @NonNls public static final String REFERENCE = "REFERENCE";
  @NonNls public static final String TYPE = "TYPE";
  @NonNls public static final String EXPECTED_TYPE = "EXPECTED TYPE";
  @NonNls public static final String MINIMUM_ZERO = "MINIMUM ZERO";
  @NonNls public static final String MAXIMUM_UNLIMITED = "MAXIMUM UNLIMITED";

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

    final EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setCaretRowShown(false);

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
      SubstitutionShortInfoHandler.install(editor, null);
    }

    return editor;
  }

  public static JComponent createOptionLine(JComponent... options) {
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

  public static void setContent(final Editor editor, String text) {
    final String value = text != null ? text : "";
    final Document document = editor.getDocument();
    CommandProcessor.getInstance().executeCommand(
      editor.getProject(),
      () -> ApplicationManager.getApplication().runWriteAction(() -> document.replaceString(0, document.getTextLength(), value)),
      MODIFY_EDITOR_CONTENT, SS_GROUP);
  }

  public static void setContent(final EditorTextField editor, String text) {
    final String value = text != null ? text : "";
    final Document document = editor.getDocument();
    CommandProcessor.getInstance().executeCommand(
      editor.getProject(),
      () -> ApplicationManager.getApplication().runWriteAction(() -> document.replaceString(0, document.getTextLength(), value)),
      MODIFY_EDITOR_CONTENT, SS_GROUP);
  }

  public static void invokeAction(Configuration config, SearchContext context) {
    if (config instanceof SearchConfiguration) {
      StructuralSearchAction.triggerAction(config, context);
    }
    else {
      StructuralReplaceAction.triggerAction(config, context);
    }
  }

  public static void updateHighlighter(Editor editor, StructuralSearchProfile profile) {
    TemplateEditorUtil.setHighlighter(editor, profile.getTemplateContextType());
  }

  public static MatchVariableConstraint getOrAddVariableConstraint(String varName, Configuration configuration) {
    MatchVariableConstraint varInfo = configuration.getMatchOptions().getVariableConstraint(varName);

    if (varInfo == null) {
      varInfo = new MatchVariableConstraint();
      varInfo.setName(varName);
      configuration.getMatchOptions().addVariableConstraint(varInfo);
    }
    return varInfo;
  }

  public static boolean isTarget(String varName, MatchOptions matchOptions) {
    if (Configuration.CONTEXT_VAR_NAME.equals(varName)) {
      // Complete Match is default target
      for (String name : matchOptions.getVariableConstraintNames()) {
        if (!name.equals(Configuration.CONTEXT_VAR_NAME)) {
          if (matchOptions.getVariableConstraint(name).isPartOfSearchResults()) {
            return false;
          }
        }
      }
      return true;
    }
    final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(varName);
    if (constraint == null) {
      return false;
    }
    return constraint.isPartOfSearchResults();
  }

  @NotNull
  public static JComponent createCompleteMatchInfo(final Supplier<? extends Configuration> configurationProducer) {
    return installCompleteMatchInfo(new JLabel(AllIcons.Actions.ListFiles), configurationProducer, null);
  }

  @NotNull
  public static JComponent installCompleteMatchInfo(JLabel completeMatchInfo,
                                                    Supplier<? extends Configuration> configurationProducer,
                                                    Consumer<String> linkConsumer) {
    completeMatchInfo.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent ignore) {
        final Configuration configuration = configurationProducer.get();
        if (configuration == null) {
          return;
        }
        final MatchOptions matchOptions = configuration.getMatchOptions();
        if (matchOptions.getSearchPattern().isEmpty()) {
          return;
        }
        final MatchVariableConstraint constraint = getOrAddVariableConstraint(Configuration.CONTEXT_VAR_NAME, configuration);
        if (isTarget(Configuration.CONTEXT_VAR_NAME, matchOptions)) {
          constraint.setPartOfSearchResults(true);
        }
        final boolean link = linkConsumer != null && !Configuration.CONTEXT_VAR_NAME.equals(configuration.getCurrentVariableName());
        final String text = SSRBundle.message("complete.match.variable.tooltip.message",
                                              SubstitutionShortInfoHandler.getShortParamString(constraint, link));
        final HyperlinkListener listener = e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && linkConsumer != null) {
            linkConsumer.accept(Configuration.CONTEXT_VAR_NAME);
            IdeTooltipManager.getInstance().hideCurrentNow(true);
          }
        };
        final IdeTooltip tooltip = new TooltipWithClickableLinks(completeMatchInfo, text, listener);
        final Rectangle bounds = completeMatchInfo.getBounds();
        tooltip.setHint(true)
          .setExplicitClose(true)
          .setPreferredPosition(Balloon.Position.below)
          .setPoint(new Point(bounds.x + (bounds.width / 2) , bounds.y + bounds.height));
        IdeTooltipManager.getInstance().show(tooltip, true);
        if (linkConsumer == null) {
          configuration.setCurrentVariableName(Configuration.CONTEXT_VAR_NAME);
        }
      }
    });
    return completeMatchInfo;
  }

  public static EditorTextField createTextComponent(String text, Project project) {
    return createEditorComponent(text, "1.txt", project);
  }

  public static EditorTextField createRegexComponent(String text, Project project) {
    return createEditorComponent(text, "1.regexp", project);
  }

  public static EditorTextField createScriptComponent(String text, Project project) {
    return createEditorComponent(text, "1.groovy", project);
  }

  @NotNull
  public static EditorTextField createEditorComponent(String text, String fileName, Project project) {
    return new EditorTextField(text, project, getFileType(fileName));
  }

  private static FileType getFileType(final String fileName) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (fileType == FileTypes.UNKNOWN) fileType = FileTypes.PLAIN_TEXT;
    return fileType;
  }
}
