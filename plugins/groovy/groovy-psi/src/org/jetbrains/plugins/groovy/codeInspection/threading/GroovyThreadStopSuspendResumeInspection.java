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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.HashSet;
import java.util.Set;

public class GroovyThreadStopSuspendResumeInspection extends BaseInspection {

  private static final Set<String> METHOD_NAMES = new HashSet<>();

  static {
    METHOD_NAMES.add("stop");
    METHOD_NAMES.add("suspend");
    METHOD_NAMES.add("resume");
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Call to Thread.stop(), Thread.suspend(), or Thread.resume()";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Call to Thread.'#ref' #loc";

  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull GrMethodCallExpression grMethodCallExpression) {
      super.visitMethodCallExpression(grMethodCallExpression);
      final GrExpression methodExpression = grMethodCallExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression reference = (GrReferenceExpression) methodExpression;
      final String name = reference.getReferenceName();
      if (!METHOD_NAMES.contains(name)) {
        return;
      }
      final PsiMethod method = grMethodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !"java.lang.Thread".equals(containingClass.getQualifiedName())) {
        return;
      }
      registerMethodCallError(grMethodCallExpression);
    }
  }
}
