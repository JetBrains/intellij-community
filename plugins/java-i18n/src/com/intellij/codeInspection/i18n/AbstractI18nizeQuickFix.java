// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class AbstractI18nizeQuickFix<T extends UExpression> implements LocalQuickFix, I18nQuickFixHandler<T>, HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(I18nizeQuickFix.class);
  private static final Set<String> AUXILIARY_WORDS = Set.of("is", "the", "of", "and", "a", "an");
  private final NlsInfo.Localized myInfo;

  protected AbstractI18nizeQuickFix(NlsInfo.Localized info) {
    myInfo = info;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public final void performI18nization(final PsiFile psiFile,
                                       final Editor editor,
                                       T literalExpression,
                                       Collection<PropertiesFile> propertiesFiles,
                                       String key, String value, String i18nizedText,
                                       UExpression[] parameters,
                                       final PropertyCreationHandler propertyCreationHandler) throws IncorrectOperationException {
    Project project = psiFile.getProject();
    propertyCreationHandler.createProperty(project, propertiesFiles, key, value, parameters);
    try {
      doReplacement(psiFile, editor, literalExpression, i18nizedText);
    }
    catch (IncorrectOperationException e) {
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project,
                                                                                     JavaI18nBundle
                                                                                       .message(
                                                                                         "inspection.i18n.expression.is.invalid.error.message"),
                                                                                     JavaI18nBundle
                                                                                       .message("inspection.error.dialog.title")));
    }
  }

  @Override
  public final void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    doFix(descriptor, project);
  }

  abstract protected void doReplacement(@NotNull PsiFile psiFile,
                                        Editor editor,
                                        T literalExpression,
                                        String i18nizedText) throws IncorrectOperationException;

  protected static void doDocumentReplacement(@NotNull PsiFile psiFile,
                                              UElement literalExpression,
                                              String i18nizedText,
                                              Document document,
                                              @Nullable UastCodeGenerationPlugin generationPlugin) {
    PsiElement psi = literalExpression.getSourcePsi();
    if (psi == null) {
      return;
    }
    Language language = psi.getLanguage();
    int startOffset = psi.getTextRange().getStartOffset();
    document.replaceString(startOffset, psi.getTextRange().getEndOffset(), i18nizedText);
    PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);
    if (generationPlugin == null) {
      return;
    }
    PsiElement notShortenPsi =
      CodeInsightUtilCore.findElementInRange(psiFile, startOffset, startOffset + i18nizedText.length(), PsiElement.class, language);
    shortenReferences(notShortenPsi, generationPlugin);
  }

  private static void shortenReferences(@Nullable PsiElement element, @NotNull UastCodeGenerationPlugin generationPlugin) {
    UElement uElement = UastContextKt.toUElement(element);
    if (uElement == null) {
      return;
    }
    // Here we rely on that replacing same element will shorten references
    generationPlugin.replace(uElement, uElement, UElement.class);
  }

  @NotNull
  protected I18nizeQuickFixDialog.DialogCustomization getCustomization(String value) {
    return new I18nizeQuickFixDialog.DialogCustomization(null, true, false, null, getSuggestedName(value, myInfo));
  }

  private void doFix(final ProblemDescriptor descriptor, final Project project) {
    final PsiElement psi = descriptor.getPsiElement();
    final PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
    if (!JavaI18nizeQuickFixDialog.isAvailable(psiFile)) {
      return;
    }
    T uast = getEnclosingLiteral(psiFile, PsiEditorUtil.findEditor(psiFile));
    final JavaI18nizeQuickFixDialog<T> dialog = createDialog(project, psiFile, uast);
    if (!dialog.showAndGet()) {
      return;
    }
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();

    if (!FileModificationService.getInstance().preparePsiElementForWrite(psi)) return;
    for (PropertiesFile file : propertiesFiles) {
      if (file.findPropertyByKey(dialog.getKey()) == null &&
          !FileModificationService.getInstance().prepareFileForWrite(file.getContainingFile())) return;
    }

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        performI18nization(psiFile, PsiEditorUtil.findEditor(psiFile), dialog.getLiteralExpression(), propertiesFiles, dialog.getKey(),
                           dialog.getValue(), dialog.getI18nizedText(), dialog.getParameters(), dialog.getPropertyCreationHandler());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), PropertiesBundle.message("quickfix.i18n.command.name"), project);
  }

  protected abstract JavaI18nizeQuickFixDialog<T> createDialog(Project project, PsiFile context, @NotNull T concatenation);

  protected static String getSuggestedName(String value, NlsInfo.Localized info) {
    String prefix = info.getPrefix();
    String suffix = info.getSuffix();
    if (prefix.isEmpty() && suffix.isEmpty()) return null;
    if (!prefix.isEmpty()) {
      prefix += ".";
    }
    if (!suffix.isEmpty()) {
      suffix = "." + suffix;
    }
    String payload = I18nizeQuickFixDialog.generateDefaultPropertyKey(value);
    payload = Stream.of(payload.split("\\."))
      .filter(s -> !s.matches("\\d+") && !AUXILIARY_WORDS.contains(s)).collect(Collectors.joining("."));
    return prefix + payload + suffix;
  }
}
