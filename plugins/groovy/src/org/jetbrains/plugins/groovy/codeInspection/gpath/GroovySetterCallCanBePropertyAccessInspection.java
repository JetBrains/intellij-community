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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovySetterCallCanBePropertyAccessInspection extends BaseInspection {
  private final ReplaceWithPropertyAccessFix fix = new ReplaceWithPropertyAccessFix();

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GPATH;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Setter call can be property access";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Call to '#ref' can be property access #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  public GroovyFix buildFix(PsiElement location) {
    return fix;
  }

  private static class
      ReplaceWithPropertyAccessFix extends GroovyFix {
    @NotNull
    public String getName() {
      return "Replace with property access";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement referenceName = descriptor.getPsiElement();
      final String setter = referenceName.getText();
      final String propertyName = Character.toLowerCase(setter.charAt(3)) + setter.substring(4);
      final GrReferenceExpression invokedExpression = (GrReferenceExpression) referenceName.getParent();
      final GrMethodCallExpression callExpression = (GrMethodCallExpression) invokedExpression.getParent();
      final GrArgumentList args = callExpression.getArgumentList();
      assert args != null;
      final GrExpression arg = args.getExpressionArguments()[0];
      replaceExpression(callExpression, invokedExpression.getQualifierExpression().getText() + '.' + propertyName + " = " + arg.getText());
    }
  }

  private static class Visitor extends BaseInspectionVisitor {
    @NonNls private static final String SET_PREFIX = "set";

    public void visitMethodCallExpression(GrMethodCallExpression grMethodCallExpression) {
      super.visitMethodCallExpression(grMethodCallExpression);
      final GrArgumentList args = grMethodCallExpression.getArgumentList();
      if (args == null) {
        return;
      }
      if (args.getExpressionArguments().length != 1) {
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
      if (name == null || !name.startsWith(SET_PREFIX)) {
        return;
      }
      if (SET_PREFIX.equals(name)) {
        return;
      }
      String tail = StringUtil.trimStart(name, SET_PREFIX);
      // If doesn't conform to getter's convention
      if (!tail.equals(StringUtil.capitalize(tail))) {
        return;
      }
      final GrExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null || PsiUtil.isThisOrSuperRef(qualifier)) {
        return;
      }
      registerMethodCallError(grMethodCallExpression);
    }
  }
}