// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.StructuralSearchAction;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.ui.EditorTextField;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Maxim.Mossienko
 */
public final class UIUtil {
  @NonNls private static final String SS_GROUP = "structuralsearchgroup";

  @NonNls public static final String SSR_NOTIFICATION_GROUP_ID = "Structural Search";

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

  public static void setContent(@NotNull EditorTextField editor, @NotNull String text) {
    final Document document = editor.getDocument();
    WriteCommandAction.runWriteCommandAction(editor.getProject(), SSRBundle.message("modify.editor.content.command.name"), SS_GROUP,
                                             () -> document.replaceString(0, document.getTextLength(), text));
  }

  public static void invokeAction(@NotNull Configuration config, @NotNull SearchContext context) {
    StructuralSearchAction.triggerAction(config, context, !(config instanceof SearchConfiguration));
  }

  @NotNull
  public static MatchVariableConstraint getOrAddVariableConstraint(@NotNull String varName, @NotNull Configuration configuration) {
    final MatchOptions options = configuration.getMatchOptions();
    final MatchVariableConstraint varInfo = options.getVariableConstraint(varName);

    if (varInfo != null) {
      return varInfo;
    }
    return configuration.getMatchOptions().addNewVariableConstraint(varName);
  }

  @NotNull
  public static ReplacementVariableDefinition getOrAddReplacementVariable(@NotNull String varName, @NotNull Configuration configuration) {
    final ReplaceOptions replaceOptions = configuration.getReplaceOptions();
    ReplacementVariableDefinition definition = replaceOptions.getVariableDefinition(varName);

    if (definition != null) {
      return definition;
    }
    return replaceOptions.addNewVariableDefinition(varName);
  }

  public static boolean isTarget(@NotNull String varName, @NotNull MatchOptions matchOptions) {
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
  public static EditorTextField createTextComponent(@NotNull String text, @NotNull Project project) {
    return createEditorComponent(text, "1.txt", project);
  }

  @NotNull
  public static EditorTextField createRegexComponent(@NotNull String text, @NotNull Project project) {
    return createEditorComponent(text, "1.regexp", project);
  }

  @NotNull
  public static EditorTextField createScriptComponent(@NotNull String text, @NotNull Project project) {
    return createEditorComponent(text, "1.groovy", project);
  }

  @NotNull
  public static EditorTextField createEditorComponent(@NotNull String text, @NotNull String fileName, @NotNull Project project) {
    final FileType fileType = getFileType(fileName);
    final Document document = createDocument(fileType, text, project);
    return new EditorTextField(document, project, fileType);
  }

  @NotNull
  public static Document createDocument(@NotNull FileType fileType, @NotNull String text, @NotNull Project project) {
    final PsiFile file =
      PsiFileFactory.getInstance(project).createFileFromText("Dummy." + fileType.getDefaultExtension(), fileType, text, -1, true);
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    assert document != null;
    return document;
  }

  @NotNull
  private static FileType getFileType(@NotNull String fileName) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (fileType == FileTypes.UNKNOWN) fileType = FileTypes.PLAIN_TEXT;
    return fileType;
  }

  @NotNull
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
        LanguageFileType fileType = profile.detectFileType(context);
        if (fileType == null) {
          fileType = language.getAssociatedFileType();
        }
        if (fileType != null) return fileType;
      }
    }
    return StructuralSearchUtil.getDefaultFileType();
  }

  @NotNull
  public static Document createDocument(@NotNull Project project, @Nullable LanguageFileType fileType, Language dialect,
                                        PatternContext patternContext, @NotNull String text, @Nullable StructuralSearchProfile profile) {
    if (fileType != null && profile != null) {
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

  public static TemplateContextType getTemplateContextType(@NotNull StructuralSearchProfile profile) {
    final Class<? extends TemplateContextType> clazz = profile.getTemplateContextTypeClass();
    return ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), clazz);
  }
}
