// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.evaluation;

import com.intellij.lang.Language;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SlowOperations;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class XDebuggerEditorsProviderBase extends XDebuggerEditorsProvider {
  @NotNull
  @Override
  public Document createDocument(@NotNull Project project,
                                 @NotNull XExpression expression,
                                 @Nullable XSourcePosition sourcePosition,
                                 @NotNull EvaluationMode mode) {
    PsiElement context = null;
    if (sourcePosition != null) {
      context = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
    }
    return createDocument(project, expression, context, mode);
  }

  @NotNull
  public Document createDocument(@NotNull Project project,
                                 @NotNull XExpression expression,
                                 @Nullable PsiElement context,
                                 @NotNull EvaluationMode mode) {
    PsiFile codeFragment;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-304707, EA-597817, EA-832153, ...")) {
      codeFragment = createExpressionCodeFragment(project, expression, context, true);
    }
    Document document = codeFragment.getViewProvider().getDocument();
    assert document != null;
    return document;
  }

  protected abstract PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull String text, @Nullable PsiElement context, boolean isPhysical);

  protected PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull XExpression expression, @Nullable PsiElement context, boolean isPhysical) {
    return createExpressionCodeFragment(project, expression.getExpression(), context, isPhysical);
  }

  @NotNull
  public Collection<Language> getSupportedLanguages(@Nullable PsiElement context) {
    if (context != null) {
      return getSupportedLanguages(context.getProject(), null);
    }
    return Collections.emptyList();
  }

  @Nullable
  protected PsiElement getContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project) {
    return XDebuggerUtil.getInstance().findContextElement(virtualFile, offset, project, false);
  }
}