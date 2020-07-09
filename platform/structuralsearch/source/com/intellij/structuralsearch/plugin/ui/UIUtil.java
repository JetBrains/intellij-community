// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.command.WriteCommandAction;
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
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.StructuralSearchAction;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TooltipWithClickableLinks;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
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
public final class UIUtil {
  @NonNls private static final String SS_GROUP = "structuralsearchgroup";

  public static final NotificationGroup SSR_NOTIFICATION_GROUP =
    new NotificationGroup("Structural Search", NotificationDisplayType.STICKY_BALLOON, true, ToolWindowId.FIND, null,
                          SSRBundle.message("structural.search.title"), null);

  @NonNls public static final String TEXT = "TEXT";
  @NonNls public static final String TEXT_HIERARCHY = "TEXT HIERARCHY";
  @NonNls public static final String REFERENCE = "REFERENCE";
  @NonNls public static final String TYPE = "TYPE";
  @NonNls public static final String TYPE_REGEX = "TYPE REGEX";
  @NonNls public static final String EXPECTED_TYPE = "EXPECTED TYPE";
  @NonNls public static final String MINIMUM_ZERO = "MINIMUM ZERO";
  @NonNls public static final String MAXIMUM_UNLIMITED = "MAXIMUM UNLIMITED";
  @NonNls public static final String CONTEXT = "CONTEXT";

  private UIUtil() {
  }

  @NotNull
  public static Editor createEditor(@NotNull Document doc, Project project, boolean editable, @Nullable TemplateContextType contextType) {
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
    return editor;
  }

  public static void setContent(@NotNull final Editor editor, String text) {
    final String value = text != null ? text : "";
    final Document document = editor.getDocument();
    WriteCommandAction.runWriteCommandAction(editor.getProject(), SSRBundle.message("modify.editor.content.command.name"), SS_GROUP,
                                             () -> document.replaceString(0, document.getTextLength(), value));
  }

  public static void setContent(@NotNull EditorTextField editor, String text) {
    final String value = text != null ? text : "";
    final Document document = editor.getDocument();
    WriteCommandAction.runWriteCommandAction(editor.getProject(), SSRBundle.message("modify.editor.content.command.name"), SS_GROUP,
                                             () -> document.replaceString(0, document.getTextLength(), value));
  }

  public static void invokeAction(Configuration config, SearchContext context) {
    StructuralSearchAction.triggerAction(config, context, !(config instanceof SearchConfiguration));
  }

  public static MatchVariableConstraint getOrAddVariableConstraint(String varName, Configuration configuration) {
    final MatchOptions options = configuration.getMatchOptions();
    final MatchVariableConstraint varInfo = options.getVariableConstraint(varName);

    if (varInfo != null) {
      return varInfo;
    }
    return configuration.getMatchOptions().addNewVariableConstraint(varName);
  }

  public static ReplacementVariableDefinition getOrAddReplacementVariable(String varName, Configuration configuration) {
    final ReplaceOptions replaceOptions = configuration.getReplaceOptions();
    ReplacementVariableDefinition definition = replaceOptions.getVariableDefinition(varName);

    if (definition != null) {
      return definition;
    }
    return replaceOptions.addNewVariableDefinition(varName);
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
                                                    Consumer<? super String> linkConsumer) {
    completeMatchInfo.putClientProperty(IdeTooltip.TOOLTIP_DISMISS_DELAY_KEY, 20000);
    completeMatchInfo.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent ignore) {
        if (Registry.is("ssr.use.editor.inlays.instead.of.tool.tips")) {
          return;
        }
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
        String filterText = StringUtil.escapeXmlEntities(
          SSRBundle.message("complete.match.variable.tooltip.message",
                            SubstitutionShortInfoHandler.getShortParamString(constraint, linkConsumer == null)));
        if (linkConsumer != null && !Configuration.CONTEXT_VAR_NAME.equals(configuration.getCurrentVariableName())) {
          filterText = SubstitutionShortInfoHandler.appendLinkText(filterText, Configuration.CONTEXT_VAR_NAME);
        }
        final HyperlinkListener listener = e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && linkConsumer != null) {
            linkConsumer.accept(Configuration.CONTEXT_VAR_NAME);
            IdeTooltipManager.getInstance().hideCurrentNow(true);
          }
        };
        final IdeTooltip tooltip = new TooltipWithClickableLinks(completeMatchInfo, filterText, listener);
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
    final FileType fileType = getFileType(fileName);
    final Document document = createDocument(fileType, text, project);
    return new EditorTextField(document, project, fileType);
  }

  @NotNull
  public static Document createDocument(FileType fileType, String text, Project project) {
    final PsiFile file =
      PsiFileFactory.getInstance(project).createFileFromText("Dummy." + fileType.getDefaultExtension(), fileType, text, -1, true);
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    assert document != null;
    return document;
  }

  private static FileType getFileType(final String fileName) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (fileType == FileTypes.UNKNOWN) fileType = FileTypes.PLAIN_TEXT;
    return fileType;
  }

  public static LanguageFileType detectFileType(@NotNull SearchContext searchContext) {
    final PsiFile file = searchContext.getFile();
    PsiElement context = null;

    final Editor editor = searchContext.getEditor();
    if (editor != null && file != null) {
      final int offset = editor.getCaretModel().getOffset();
      context = InjectedLanguageManager.getInstance(searchContext.getProject()).findInjectedElementAt(file, offset);
      if (context == null) {
        context = file.findElementAt(offset);
      }
      if (context != null) {
        context = context.getParent();
      }
      if (context == null) {
        context = file;
      }
    }
    if (context != null) {
      final Language language = context.getLanguage();
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(language);
      if (profile != null) {
        final LanguageFileType fileType = profile.detectFileType(context);
        return fileType != null ? fileType : language.getAssociatedFileType();
      }
    }
    return StructuralSearchUtil.getDefaultFileType();
  }

  @NotNull
  public static Document createDocument(@NotNull Project project, @NotNull LanguageFileType fileType, Language dialect,
                                        PatternContext patternContext, @NotNull String text, @NotNull StructuralSearchProfile profile) {
    final String contextId = (patternContext == null) ? null : patternContext.getId();
    PsiFile codeFragment = profile.createCodeFragment(project, text, contextId);
    if (codeFragment == null) {
      codeFragment = createFileFragment(project, fileType, dialect, text);
    }

    if (codeFragment != null) {
      final Document doc = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
      assert doc != null : "code fragment element should be physical";
      return doc;
    }

    return EditorFactory.getInstance().createDocument(text);
  }

  @NotNull
  public static Editor createEditor(@NotNull Project project, @NotNull LanguageFileType fileType, Language dialect, @NotNull String text,
                                    boolean editable, @NotNull StructuralSearchProfile profile) {
    PsiFile codeFragment = profile.createCodeFragment(project, text, null);
    if (codeFragment == null) {
      codeFragment = createFileFragment(project, fileType, dialect, text);
    }

    final Document doc;
    if (codeFragment != null) {
      doc = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
      assert doc != null : "code fragment element should be physical";
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(codeFragment, false);
    }
    else {
      doc = EditorFactory.getInstance().createDocument("");
    }
    return createEditor(doc, project, editable, getTemplateContextType(profile));
  }

  private static PsiFile createFileFragment(@NotNull Project project, @NotNull LanguageFileType fileType, Language dialect, @NotNull String text) {
    final String name = "__dummy." + fileType.getDefaultExtension();
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);

    return dialect == null
           ? factory.createFileFromText(name, fileType, text, LocalTimeCounter.currentTime(), true, true)
           : factory.createFileFromText(name, dialect, text, true, true);
  }

  public static TemplateContextType getTemplateContextType(StructuralSearchProfile profile) {
    final Class<? extends TemplateContextType> clazz = profile.getTemplateContextTypeClass();
    return ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), clazz);
  }
}
