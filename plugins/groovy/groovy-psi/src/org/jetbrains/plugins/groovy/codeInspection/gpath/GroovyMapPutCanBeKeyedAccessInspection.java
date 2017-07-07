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
import com.intellij.psi.CommonClassNames;
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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyMapPutCanBeKeyedAccessInspection extends BaseInspection {
  private final ReplaceWithPropertyAccessFix fix = new ReplaceWithPropertyAccessFix();

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Call to Map.put can be keyed access";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Call to '#ref' can be keyed access #loc";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Override
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return fix;
  }

  private static class ReplaceWithPropertyAccessFix extends GroovyFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return "Replace with keyed access";
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement referenceName = descriptor.getPsiElement();
      final GrReferenceExpression invokedExpression = (GrReferenceExpression) referenceName.getParent();
      final GrMethodCallExpression callExpression = (GrMethodCallExpression) invokedExpression.getParent();
      final GrArgumentList argumentList = callExpression.getArgumentList();
      final GrExpression[] args = argumentList.getExpressionArguments();
      replaceExpression(callExpression, invokedExpression.getQualifierExpression().getText() +
          '[' + args[0].getText() + "]=" + args[1].getText());
    }
  }

  private static class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull GrMethodCallExpression grMethodCallExpression) {
      super.visitMethodCallExpression(grMethodCallExpression);
      final GrArgumentList args = grMethodCallExpression.getArgumentList();
      if (args.getExpressionArguments().length != 2) {
        return;
      }
      if (PsiImplUtil.hasNamedArguments(args)) {
        return;
      }
      final GrExpression methodExpression = grMethodCallExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression referenceExpression = (GrReferenceExpression) methodExpression;
      final String name = referenceExpression.getReferenceName();
      if (!"put".equals(name)) {
        return;
      }
      final GrExpression qualifier = referenceExpression.getQualifierExpression();

      if (qualifier == null || PsiUtil.isThisOrSuperRef(qualifier)) {
        return;
      }
      if (referenceExpression.getDotTokenType() == GroovyTokenTypes.mOPTIONAL_DOT) return;
      final PsiType type = qualifier.getType();
      if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        return;
      }
      registerMethodCallError(grMethodCallExpression);
    }
  }
}
