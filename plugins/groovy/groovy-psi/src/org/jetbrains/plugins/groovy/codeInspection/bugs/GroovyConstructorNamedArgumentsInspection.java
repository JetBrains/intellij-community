/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyConstructorNamedArgumentsInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Named arguments of constructor call";
  }

  @Override
  protected String buildErrorString(Object... args) {
    assert args.length == 1 && args[0] instanceof String;
    return (String)args[0];
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitNewExpression(@NotNull GrNewExpression newExpression) {
      super.visitNewExpression(newExpression);

      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;

      final GroovyResolveResult constructorResolveResult = newExpression.advancedResolve();
      final PsiElement constructor = constructorResolveResult.getElement();
      if (constructor != null) {
        final GrArgumentList argList = newExpression.getArgumentList();
        if (argList != null &&
            argList.getExpressionArguments().length == 0 &&
            !PsiUtil.isConstructorHasRequiredParameters((PsiMethod)constructor)) {
          checkDefaultMapConstructor(argList, constructor);
        }
      }
      else {
        final GroovyResolveResult[] results = newExpression.multiResolve(false);
        final GrArgumentList argList = newExpression.getArgumentList();
        final PsiElement element = refElement.resolve();

        if (results.length == 0 && element instanceof PsiClass) { //default constructor invocation
          PsiType[] argumentTypes = PsiUtil.getArgumentTypes(refElement, true);
          if (argumentTypes == null ||
              argumentTypes.length == 0 ||
              (argumentTypes.length == 1 &&
               InheritanceUtil.isInheritor(argumentTypes[0], CommonClassNames.JAVA_UTIL_MAP))) {
            checkDefaultMapConstructor(argList, element);
          }
        }
      }
    }

    private void checkDefaultMapConstructor(GrArgumentList argList, PsiElement element) {
      if (argList == null) return;

      final GrNamedArgument[] args = argList.getNamedArguments();
      for (GrNamedArgument arg : args) {
        final GrArgumentLabel label = arg.getLabel();
        if (label == null) continue;
        if (label.getName() == null) {
          final PsiElement nameElement = label.getNameElement();
          if (nameElement instanceof GrExpression) {
            final PsiType argType = ((GrExpression)nameElement).getType();
            if (argType != null && !TypesUtil.isAssignableByMethodCallConversion(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, arg), argType, arg)) {
              registerError(nameElement, GroovyBundle.message("property.name.expected"));
            }
          }
          else if (!"*".equals(nameElement.getText())) {
            registerError(nameElement, GroovyBundle.message("property.name.expected"));
          }
        }
        else {
          final PsiElement resolved = label.resolve();
          if (resolved == null) {

            if (element instanceof PsiMember && !(element instanceof PsiClass)) {
              element = ((PsiMember)element).getContainingClass();
            }

            List<LocalQuickFix> fixes = new ArrayList<>(2);
            if (element instanceof GrTypeDefinition) {
              fixes.add(GroovyQuickFixFactory.getInstance().createCreateFieldFromConstructorLabelFix((GrTypeDefinition)element, label.getNamedArgument()));
            }
            if (element instanceof PsiClass) {
              fixes.add(GroovyQuickFixFactory.getInstance().createDynamicPropertyFix(label, (PsiClass)element));
            }

            registerError(label, GroovyBundle.message("no.such.property", label.getName()), fixes.toArray(new LocalQuickFix[fixes.size()]),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
      }
    }
  }
}
