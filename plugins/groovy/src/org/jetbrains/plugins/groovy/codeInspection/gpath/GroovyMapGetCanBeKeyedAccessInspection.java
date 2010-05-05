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
package org.jetbrains.plugins.groovy.codeInspection.gpath;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class GroovyMapGetCanBeKeyedAccessInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GPATH;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Call to Map.get can be keyed access";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Call to '#ref' can be keyed access #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  public GroovyFix buildFix(PsiElement location) {
    return new ReplaceWithPropertyAccessFix();
  }

  private static class ReplaceWithPropertyAccessFix extends GroovyFix {

    @NotNull
    public String getName() {
      return "Replace with keyed access";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement referenceName = descriptor.getPsiElement();
      final GrReferenceExpression invokedExpression = (GrReferenceExpression) referenceName.getParent();
      final GrMethodCallExpression callExpression = (GrMethodCallExpression) invokedExpression.getParent();
      final GrArgumentList args = callExpression.getArgumentList();
      assert args != null;
      final GrExpression arg = args.getExpressionArguments()[0];
      replaceExpression(callExpression, invokedExpression.getQualifierExpression().getText() + '[' + arg.getText() + ']');
    }
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitMethodCallExpression(GrMethodCallExpression grMethodCallExpression) {
      super.visitMethodCallExpression(grMethodCallExpression);
      final GrArgumentList args = grMethodCallExpression.getArgumentList();
      if (args == null) {
        return;
      }
      if (args.getExpressionArguments().length != 1) {
        return;
      }
      if (args.getNamedArguments().length != 0) {
        return;
      }
      final GrExpression methodExpression = grMethodCallExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression referenceExpression = (GrReferenceExpression) methodExpression;
      final String name = referenceExpression.getReferenceName();
      if (!"get".equals(name)) {
        return;
      }
      final GrExpression qualifier = referenceExpression.getQualifierExpression();

      if (qualifier == null ||
          qualifier instanceof GrThisReferenceExpression ||
          qualifier instanceof GrSuperReferenceExpression) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (!InheritanceUtil.isInheritor(type, "java.util.Map")) {
        return;
      }
      registerMethodCallError(grMethodCallExpression);
    }
  }
}
