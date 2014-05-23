/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.exception;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

class CatchParameterUsedVisitor extends GroovyRecursiveElementVisitor {

  private final GrParameter parameter;
  private boolean used = false;

  CatchParameterUsedVisitor(GrParameter variable) {
    super();
    parameter = variable;
  }

  @Override
  public void visitElement(@NotNull GroovyPsiElement element) {
    if (!used) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    if (used) {
      return;
    }
    super.visitReferenceExpression(referenceExpression);
    final PsiElement element = referenceExpression.resolve();
    if (parameter.equals(element)) {
      used = true;
    }
  }

  public void visitJSReferenceExpression(@NotNull GrReferenceExpression reference) {
    if (used) {
      return;
    }
    super.visitReferenceExpression(reference);
    final PsiElement element = reference.resolve();
    if (parameter.equals(element)) {
      used = true;
    }
  }

  public boolean isUsed() {
    return used;
  }
}
