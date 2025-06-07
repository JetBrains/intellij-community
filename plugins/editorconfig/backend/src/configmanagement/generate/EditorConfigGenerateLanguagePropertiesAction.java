// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.generate;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.editorconfig.common.EditorConfigBundle;
import com.intellij.editorconfig.common.syntax.psi.*;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.LightEditActionFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.ec4j.core.model.Ec4jPath;
import org.ec4j.core.model.Glob;
import org.editorconfig.configmanagement.export.EditorConfigSettingsWriter;
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EditorConfigGenerateLanguagePropertiesAction extends CodeInsightAction {

  private static final CodeInsightActionHandler HANDLER = new LanguagePropertiesGenerator();
  private static final Logger LOG = Logger.getInstance(EditorConfigGenerateLanguagePropertiesAction.class);

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return HANDLER;
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project,
                                   @NotNull Editor editor,
                                   @NotNull PsiFile psiFile) {
    if (psiFile instanceof EditorConfigPsiFile) {
      int currOffset = editor.getCaretModel().getOffset();
      if (currOffset == psiFile.getTextLength() && currOffset > 0) currOffset --;
      PsiElement contextElement = psiFile.findElementAt(currOffset);
      if (contextElement != null) {
        if (contextElement instanceof PsiWhiteSpace) {
          PsiElement prev = contextElement.getPrevSibling();
          return prev != null &&
                 (prev.getNode().getElementType() == EditorConfigElementTypes.SECTION ||
                  prev.getNode().getElementType() == EditorConfigElementTypes.HEADER ||
                  prev instanceof EditorConfigOption);
        }
        else if (contextElement.getNode().getElementType() == EditorConfigElementTypes.IDENTIFIER) {
          return contextElement.getTextRange().getStartOffset() == currOffset;
        }
      }
    }
    return false;
  }

  @TestOnly
  public static void generateProperties(@NotNull Project project, @NotNull Editor editor, @NotNull Language language) {
    LanguagePropertiesGenerator.generateProperties(project, editor, language);
  }

  private static class LanguagePropertiesGenerator implements CodeInsightActionHandler {
    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull Editor editor, @NotNull PsiFile psiFile) {
      ListPopup languagePopup = createLanguagePopup(project, psiFile, editor);
      languagePopup.showInBestPositionFor(editor);
    }

    private static void generateProperties(@NotNull Project project, @NotNull Editor editor, @NotNull Language language) {
      LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(language);
      if (provider != null) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (
          EditorConfigSettingsWriter writer =
            new EditorConfigSettingsWriter(project, output, CodeStyle.getSettings(project), false, false)
              .forLanguages(language)
              .forPropertyKinds(EditorConfigPropertyKind.LANGUAGE)
              .withoutHeaders()) {
          writer.writeSettings();
          writer.flush();
          String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
          CommandProcessor.getInstance().runUndoTransparentAction(
            () -> ApplicationManager.getApplication().runWriteAction(
              () -> {
                editor.getDocument().insertString(editor.getCaretModel().getOffset(), text);
              }
            )
          );
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    private static ListPopup createLanguagePopup(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor) {
      DefaultActionGroup languageGroup = new DefaultActionGroup();
      Set<Language> preferredLanguages = getPreferredLanguages(file, editor.getCaretModel().getOffset());
      getSupportedLanguages().stream()
                             .sorted((l1, l2) -> {
                               if (preferredLanguages == null) return 0;
                               return (preferredLanguages.contains(l2) ? 1 : 0) - (preferredLanguages.contains(l1) ? 1 : 0);
                             })
                             .forEach(
                               language -> {
                                 languageGroup.add(
                                   LightEditActionFactory
                                     .create(language.getDisplayName(), event -> generateProperties(project, editor, language)));
                               }
                             );
      return JBPopupFactory.getInstance().createActionGroupPopup(
        EditorConfigBundle.message("popup.title.choose.language"),
        languageGroup,
        DataManager.getInstance().getDataContext(editor.getComponent()),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false);
    }

    private static List<Language> getSupportedLanguages() {
      return
        LanguageCodeStyleSettingsProvider.getAllProviders()
                                                 .stream()
                                                 .filter(provider -> provider.supportsExternalFormats())
                                                 .map(provider -> provider.getLanguage())
                                                 .sorted((l1, l2) -> l1.getDisplayName().compareToIgnoreCase(l2.getDisplayName()))
                                                 .collect(Collectors.toList());
    }

    private static @Nullable Set<Language> getPreferredLanguages(@NotNull PsiFile file, int offset) {
      Set<Language> languages = new HashSet<>();
      int currOffset = offset == file.getTextLength() && offset > 0 ? offset - 1 : offset;
      PsiElement contextElement = file.findElementAt(currOffset);
      if (contextElement != null) {
        EditorConfigHeader header = findHeader(contextElement);
        if (header != null) {
          String pattern = header.getText();
          pattern = StringUtil.trimEnd(StringUtil.trimStart(pattern, "["), "]").trim();
          if ("*".equals(pattern)) return null;
          for (FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
            if (fileType instanceof LanguageFileType) {
              String testName = "/a." + fileType.getDefaultExtension();
              // TODO verify that the pattern ends up being created from the same string
              //if (EditorConfig.filenameMatches("", pattern, testName)) {
              if (new Glob(pattern).match(Ec4jPath.Ec4jPaths.of(testName))) {
                languages.add(((LanguageFileType)fileType).getLanguage());
              }
            }
          }
        }
      }
      return languages;
    }

    private static @Nullable EditorConfigHeader findHeader(@NotNull PsiElement contextElement) {
      if (contextElement instanceof PsiWhiteSpace) {
        PsiElement prev = contextElement.getPrevSibling();
        if (prev instanceof EditorConfigSection) {
          return ((EditorConfigSection)prev).getHeader();
        }
      }
      PsiElement parent = contextElement.getParent();
      while (parent != null && !(parent instanceof PsiFile)) {
        if (parent instanceof EditorConfigSection) {
          return ((EditorConfigSection)parent).getHeader();
        }
        parent = parent.getParent();
      }
      return null;
    }
  }
}
