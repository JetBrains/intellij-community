// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.evaluation;

import com.intellij.lang.Language;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SlowOperations;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

public abstract class XDebuggerEditorsProviderBase extends XDebuggerEditorsProvider {
  public static final Key<Boolean> DEBUGGER_FILE_KEY = Key.create("debugger.file");
  @Override
  public @NotNull Document createDocument(@NotNull Project project,
                                          @NotNull XExpression expression,
                                          @Nullable XSourcePosition sourcePosition,
                                          @NotNull EvaluationMode mode) {
    PsiElement context = null;
    if (sourcePosition != null) {
      context = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
    }
    return createDocument(project, expression, context, mode);
  }

  public @NotNull Document createDocument(@NotNull Project project,
                                          @NotNull XExpression expression,
                                          @Nullable PsiElement context,
                                          @NotNull EvaluationMode mode) {
    PsiFile codeFragment;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-304707, EA-597817, EA-832153, ...")) {
      codeFragment = createExpressionCodeFragment(project, expression, context, true);
    }
    codeFragment.putUserData(DEBUGGER_FILE_KEY, true);
    Document document = codeFragment.getViewProvider().getDocument();
    assert document != null;
    return document;
  }

  protected abstract PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull String text, @Nullable PsiElement context, boolean isPhysical);

  protected PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull XExpression expression, @Nullable PsiElement context, boolean isPhysical) {
    return createExpressionCodeFragment(project, expression.getExpression(), context, isPhysical);
  }

  public @NotNull @Unmodifiable Collection<Language> getSupportedLanguages(@Nullable PsiElement context) {
    if (context != null) {
      return getSupportedLanguages(context.getProject(), null);
    }
    return Collections.emptyList();
  }

  protected @Nullable PsiElement getContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project) {
    return XDebuggerUtil.getInstance().findContextElement(virtualFile, offset, project, false);
  }
}