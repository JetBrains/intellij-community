/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  private final Project project;
  private final Editor editor;
  @Nullable private final GrExpression expression;
  private final PsiElement[] occurrences;
  private final PsiElement scope;
  @Nullable private final GrVariable var;
  @NotNull private final PsiElement place;
  private final StringPartInfo myStringPart;

  public GrIntroduceContextImpl(@NotNull Project project,
                                Editor editor,
                                @Nullable GrExpression expression,
                                @Nullable GrVariable var,
                                @Nullable StringPartInfo stringPart,
                                @NotNull PsiElement[] occurrences,
                                PsiElement scope) {
    myStringPart = stringPart;
    LOG.assertTrue(expression != null || var != null || stringPart != null);

    this.project = project;
    this.editor = editor;
    this.expression = expression;
    this.occurrences = occurrences;
    this.scope = scope;
    this.var = var;
    this.place = GrIntroduceHandlerBase.getCurrentPlace(expression, var, stringPart);
  }

  @NotNull
  public Project getProject() {
    return project;
  }

  public Editor getEditor() {
    return editor;
  }

  @Nullable
  public GrExpression getExpression() {
    return expression;
  }

  @NotNull
  public PsiElement[] getOccurrences() {
    return occurrences;
  }

  public PsiElement getScope() {
    return scope;
  }

  @Nullable
  public GrVariable getVar() {
    return var;
  }

  @Nullable
  @Override
  public StringPartInfo getStringPart() {
    return myStringPart;
  }

  @NotNull
  public PsiElement getPlace() {
    return place;
  }
}
