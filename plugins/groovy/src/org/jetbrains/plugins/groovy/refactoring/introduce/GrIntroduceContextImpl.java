// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceContextImpl implements GrIntroduceContext {
  private static final Logger LOG = Logger.getInstance(GrIntroduceContextImpl.class);

  private final Project myProject;
  private final Editor myEditor;
  private final @Nullable GrExpression myExpression;
  private final PsiElement[] myOccurrences;
  private final PsiElement myScope;
  private final @Nullable GrVariable myVar;
  private final @NotNull PsiElement myPlace;
  private final StringPartInfo myStringPart;

  public GrIntroduceContextImpl(@NotNull Project project,
                                Editor editor,
                                @Nullable GrExpression expression,
                                @Nullable GrVariable var,
                                @Nullable StringPartInfo stringPart,
                                PsiElement @NotNull [] occurrences,
                                PsiElement scope) {
    myStringPart = stringPart;
    LOG.assertTrue(expression != null || var != null || stringPart != null);

    myProject = project;
    myEditor = editor;
    myExpression = expression;
    myOccurrences = occurrences;
    myScope = scope;
    myVar = var;
    myPlace = GrIntroduceHandlerBase.getCurrentPlace(expression, var, stringPart);
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public @Nullable GrExpression getExpression() {
    return myExpression;
  }

  @Override
  public PsiElement @NotNull [] getOccurrences() {
    return myOccurrences;
  }

  @Override
  public PsiElement getScope() {
    return myScope;
  }

  @Override
  public @Nullable GrVariable getVar() {
    return myVar;
  }

  @Override
  public @Nullable StringPartInfo getStringPart() {
    return myStringPart;
  }

  @Override
  public @NotNull PsiElement getPlace() {
    return myPlace;
  }
}
