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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiThisExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

class RecursionVisitor extends GroovyRecursiveElementVisitor {

  private boolean recursive = false;
  private final GrMethod method;
  private final String methodName;

  public RecursionVisitor(@NotNull GrMethod method) {
    super();
    this.method = method;
    methodName = method.getName();
  }

  @Override
  public void visitElement(@NotNull GroovyPsiElement element) {
    if (!recursive) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitMethodCallExpression(
      @NotNull GrMethodCallExpression call) {
    if (recursive) {
      return;
    }
    super.visitMethodCallExpression(call);
    final GrExpression methodExpression =
        call.getInvokedExpression();
    if (!(methodExpression instanceof GrReferenceExpression)) {
      return;
    }
    final GrReferenceExpression referenceExpression = (GrReferenceExpression) methodExpression;
    final String calledMethodName = referenceExpression.getReferenceName();
    if (calledMethodName == null) {
      return;
    }
    if (!calledMethodName.equals(methodName)) {
      return;
    }
    final PsiMethod calledMethod = call.resolveMethod();
    if (!method.equals(calledMethod)) {
      return;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.PRIVATE)) {
      recursive = true;
      return;
    }
    final GrExpression qualifier = referenceExpression.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiThisExpression) {
      recursive = true;
    }
  }

  public boolean isRecursive() {
    return recursive;
  }
}
