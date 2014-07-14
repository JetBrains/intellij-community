/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  private final Project myProject;
  private final Editor myEditor;
  @Nullable private final GrExpression myExpression;
  private final PsiElement[] myOccurrences;
  private final PsiElement myScope;
  @Nullable private final GrVariable myVar;
  @NotNull private final PsiElement myPlace;
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

    myProject = project;
    myEditor = editor;
    myExpression = expression;
    myOccurrences = occurrences;
    myScope = scope;
    myVar = var;
    myPlace = GrIntroduceHandlerBase.getCurrentPlace(expression, var, stringPart);
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  @Nullable
  public GrExpression getExpression() {
    return myExpression;
  }

  @Override
  @NotNull
  public PsiElement[] getOccurrences() {
    return myOccurrences;
  }

  @Override
  public PsiElement getScope() {
    return myScope;
  }

  @Override
  @Nullable
  public GrVariable getVar() {
    return myVar;
  }

  @Nullable
  @Override
  public StringPartInfo getStringPart() {
    return myStringPart;
  }

  @Override
  @NotNull
  public PsiElement getPlace() {
    return myPlace;
  }
}
