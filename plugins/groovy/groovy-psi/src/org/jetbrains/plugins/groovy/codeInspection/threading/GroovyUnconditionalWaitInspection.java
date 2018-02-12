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
package org.jetbrains.plugins.groovy.codeInspection.threading;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyUnconditionalWaitInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Unconditional 'wait' call";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return "Unconditional call to <code>#ref()</code> #loc";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnconditionalWaitVisitor();
  }

  private static class UnconditionalWaitVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull GrMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return;
      }
      final GrCodeBlock body = method.getBlock();
      if (body != null) {
        checkBody(body);
      }
    }

    @Override
    public void visitSynchronizedStatement(@NotNull GrSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final GrCodeBlock body = statement.getBody();
      if (body != null) {
        checkBody(body);
      }
    }

    private void checkBody(GrCodeBlock body) {
      final GrStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return;
      }
      for (final GrStatement statement : statements) {
        if (isConditional(statement)) {
          return;
        }

        if (!(statement instanceof GrMethodCallExpression)) {
          continue;
        }
        final GrMethodCallExpression methodCallExpression =
            (GrMethodCallExpression) statement;
        final GrExpression methodExpression = methodCallExpression.getInvokedExpression();
        if (!(methodExpression instanceof GrReferenceExpression)) {
          return;
        }
        final GrReferenceExpression reference = (GrReferenceExpression) methodExpression;
        final String name = reference.getReferenceName();
        if (!"wait".equals(name)) {
          return;
        }
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
          return;
        }
        registerMethodCallError(methodCallExpression);
      }
    }

    private static boolean isConditional(GrStatement statement) {
      return statement instanceof GrIfStatement;
    }
  }
}
