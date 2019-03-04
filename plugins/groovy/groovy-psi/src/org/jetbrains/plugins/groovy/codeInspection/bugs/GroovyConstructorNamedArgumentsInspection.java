// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
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

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
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

            registerError(label, GroovyBundle.message("no.such.property", label.getName()), fixes.toArray(LocalQuickFix.EMPTY_ARRAY),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
      }
    }
  }
}
