// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.StructuralReplaceAction;
import com.intellij.structuralsearch.plugin.StructuralSearchAction;
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
public class UIUtil {
  private static final String MODIFY_EDITOR_CONTENT = SSRBundle.message("modify.editor.content.command.name");
  @NonNls private static final String SS_GROUP = "structuralsearchgroup";

  public static final NotificationGroup SSR_NOTIFICATION_GROUP =
    new NotificationGroup(SSRBundle.message("structural.search.title"), NotificationDisplayType.STICKY_BALLOON, true, ToolWindowId.FIND);

  @NonNls public static final String TEXT = "TEXT";
  @NonNls public static final String TEXT_HIERARCHY = "TEXT HIERARCHY";
  @NonNls public static final String REFERENCE = "REFERENCE";
  @NonNls public static final String TYPE = "TYPE";
  @NonNls public static final String EXPECTED_TYPE = "EXPECTED TYPE";
  @NonNls public static final String MINIMUM_ZERO = "MINIMUM ZERO";
  @NonNls public static final String MAXIMUM_UNLIMITED = "MAXIMUM UNLIMITED";

  private UIUtil() {
  }

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
    final JPanel tmp = new JPanel();

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

  public static void setContent(@NotNull final Editor editor, String text) {
    final String value = text != null ? text : "";
    final Document document = editor.getDocument();
    WriteCommandAction.runWriteCommandAction(editor.getProject(), MODIFY_EDITOR_CONTENT, SS_GROUP,
                                             () -> document.replaceString(0, document.getTextLength(), value));
  }

  public static void setContent(@NotNull EditorTextField editor, String text) {
    final String value = text != null ? text : "";
    final Document document = editor.getDocument();
    WriteCommandAction.runWriteCommandAction(editor.getProject(), MODIFY_EDITOR_CONTENT, SS_GROUP,
                                             () -> document.replaceString(0, document.getTextLength(), value));
  }

  public static void invokeAction(Configuration config, SearchContext context) {
    if (config instanceof SearchConfiguration) {
      StructuralSearchAction.triggerAction(config, context);
    }
    else {
      StructuralReplaceAction.triggerAction(config, context);
    }
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
                                                    Consumer<? super String> linkConsumer) {
    completeMatchInfo.putClientProperty(IdeTooltip.TOOLTIP_DISMISS_DELAY_KEY, 20000);
    completeMatchInfo.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent ignore) {
        if (Registry.is("ssr.use.editor.inlays.instead.of.tool.tips") && Registry.is("ssr.use.new.search.dialog")) {
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
                                    @NotNull StructuralSearchProfile profile) {
    PsiFile codeFragment = profile.createCodeFragment(project, text, null);
    if (codeFragment == null) {
      codeFragment = createFileFragment(project, fileType, dialect, text);
    }

    if (codeFragment != null) {
      final Document doc = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
      assert doc != null : "code fragment element should be physical";
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(codeFragment, false);
      return createEditor(doc, project, true, true, getTemplateContextType(profile));
    }

    final EditorFactory factory = EditorFactory.getInstance();
    final Document document = factory.createDocument(text);
    final EditorEx editor = (EditorEx)factory.createEditor(document, project);
    editor.getSettings().setFoldingOutlineShown(false);
    return editor;
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
