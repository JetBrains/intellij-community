// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

class SuppressByCommentOutAction extends SuppressIntentionAction {
  private final String nonNlsCommentPattern;

  SuppressByCommentOutAction(String nonNlsCommentPattern) {
    this.nonNlsCommentPattern = nonNlsCommentPattern;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    element = findJavaCodeUpThere(element);
    PsiFile file = element.getContainingFile();
    editor = InjectedLanguageUtil.openEditorFor(file, project);
    int endOffset = element.getTextRange().getEndOffset();
    int line = editor.getDocument().getLineNumber(endOffset);
    int lineEndOffset = editor.getDocument().getLineEndOffset(line);

    PsiComment comment = PsiTreeUtil.findElementOfClassAtOffset(file, lineEndOffset-1, PsiComment.class, false);
    String prefix = "";
    boolean prefixFound = false;
    if (comment != null) {
      IElementType tokenType = comment.getTokenType();
      if (tokenType == JavaTokenType.END_OF_LINE_COMMENT) {
        prefix = StringUtil.trimStart(comment.getText(),"//") + " ";
        prefixFound = true;
      }
    }
    String commentText = "//" + prefix + nonNlsCommentPattern;
    if (prefixFound) {
      PsiComment newcom = JavaPsiFacade.getElementFactory(project).createCommentFromText(commentText, element);
      comment.replace(newcom);
    }
    else {
      editor.getDocument().insertString(lineEndOffset, " " + commentText);
    }
    DaemonCodeAnalyzerEx.getInstanceEx(project).restart("SuppressByCommentOutAction.invoke"); //comment replacement not necessarily rehighlights
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!element.isValid()) {
      return false;
    }
    // find java code up there, going through injections if necessary
    return findJavaCodeUpThere(element) != null;
  }

  private static PsiElement findJavaCodeUpThere(PsiElement element) {
    InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(element.getProject());
    while (element != null) {
      if (element.getLanguage() == JavaLanguage.INSTANCE && !injectedManager.isInjectedFragment(element.getContainingFile())) return element;
      element = element.getContext();
    }
    return null;
  }

  @Override
  public @NotNull String getFamilyName() {
    return AnalysisBundle.message("suppress.inspection.family");
  }

  @Override
  public @NotNull String getText() {
    return JavaI18nBundle.message("intention.text.suppress.with.0.comment", nonNlsCommentPattern);
  }
}
