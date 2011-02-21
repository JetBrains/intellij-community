/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.introduce;

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
public class GrIntroduceContext {
  public final Project project;
  public final Editor editor;
  @Nullable public final GrExpression expression;
  public final PsiElement[] occurrences;
  public final PsiElement scope;
  @Nullable public final GrVariable var;
  @NotNull public final PsiElement place;

  public GrIntroduceContext(Project project,
                            Editor editor,
                            GrExpression expression,
                            PsiElement[] occurrences,
                            PsiElement scope,
                            @Nullable GrVariable var) {
    this.project = project;
    this.editor = editor;
    this.expression = expression;
    this.occurrences = occurrences;
    this.scope = scope;
    this.var = var;
    this.place = expression == null ? var : expression;
  }
}
