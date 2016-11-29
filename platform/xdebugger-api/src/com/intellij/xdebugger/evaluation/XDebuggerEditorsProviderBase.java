/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.evaluation;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
  public final Document createDocument(@NotNull Project project, @NotNull String text, @Nullable XSourcePosition sourcePosition, @NotNull EvaluationMode mode) {
    PsiElement context = null;
    if (sourcePosition != null) {
      context = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
    }

    PsiFile codeFragment = createExpressionCodeFragment(project, text, context, true);
    Document document = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
    assert document != null;
    return document;
  }

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
    PsiFile codeFragment = createExpressionCodeFragment(project, expression, context, true);
    Document document = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
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