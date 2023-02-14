// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GroovyUncheckedAssignmentOfMemberOfRawTypeInspection extends BaseInspection {

  @Override
  protected String buildErrorString(Object... args) {
    PsiType expectedType = (PsiType)args[0];
    PsiType rType = (PsiType)args[1];
    return GroovyBundle.message("cannot.assign", rType.getPresentableText(), expectedType.getPresentableText());
  }

  @Override
  @NotNull
  protected BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitReturnStatement(@NotNull GrReturnStatement returnStatement) {
      final GrExpression value = returnStatement.getReturnValue();
      if (value != null) {
        final PsiType type = value.getType();
        if (type != null) {
          final GrParameterListOwner owner = PsiTreeUtil.getParentOfType(returnStatement, GrParameterListOwner.class);
          if (owner instanceof PsiMethod method) {
            if (!method.isConstructor()) {
              final PsiType methodType = method.getReturnType();
              final PsiType returnType = value.getType();
              if (methodType != null) {
                if (!PsiTypes.voidType().equals(methodType)) {
                  if (returnType != null) {
                    checkAssignability(methodType, value, value);
                  }
                }
              }
            }
          }
        }
      }
    }

    @Override
    public void visitNamedArgument(@NotNull GrNamedArgument argument) {
      final GrArgumentLabel label = argument.getLabel();
      if (label != null) {
        PsiType expectedType = label.getExpectedArgumentType();
        if (expectedType != null) {
          expectedType = TypeConversionUtil.erasure(expectedType);
          final GrExpression expr = argument.getExpression();
          if (expr != null) {
            final PsiType argType = expr.getType();
            if (argType != null) {
              final PsiClassType listType = JavaPsiFacade.getInstance(argument.getProject()).getElementFactory()
                .createTypeByFQClassName(CommonClassNames.JAVA_UTIL_LIST, argument.getResolveScope());
              if (listType.isAssignableFrom(argType)) return; //this is constructor arguments list
              checkAssignability(expectedType, expr, argument);
            }
          }
        }
      }

    }

    @Override
    public void visitVariable(@NotNull GrVariable variable) {
      PsiType varType = variable.getType();
      GrExpression initializer = variable.getInitializerGroovy();
      if (initializer != null) {
        PsiType rType = initializer.getType();
        if (rType != null) {
          checkAssignability(varType, initializer, initializer);
        }
      }

    }

    @Override
    public void visitAssignmentExpression(@NotNull GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);

      if (assignment.isOperatorAssignment()) return;

      GrExpression lValue = assignment.getLValue();
      if (!PsiUtil.mightBeLValue(lValue)) return;

      GrExpression rValue = assignment.getRValue();
      if (rValue == null) return;

      PsiType lType = lValue.getNominalType();
      PsiType rType = rValue.getType();

      // For assignments with spread dot
      if (PsiImplUtil.isSpreadAssignment(lValue) && lType instanceof PsiClassType pct) {
        final PsiClass clazz = pct.resolve();
        if (clazz != null && CommonClassNames.JAVA_UTIL_LIST.equals(clazz.getQualifiedName())) {
          final PsiType[] types = pct.getParameters();
          if (types.length == 1 && types[0] != null && rType != null) {
            checkAssignability(types[0], rValue, assignment);
          }
        }
        return;
      }
      if (lValue instanceof GrReferenceExpression &&
          ((GrReferenceExpression)lValue).resolve() instanceof GrReferenceExpression) { //lvalue is nondeclared variable
        return;
      }
      if (lType != null && rType != null) {
        checkAssignability(lType, rValue, rValue);
      }
    }

    private void checkAssignability(PsiType lType, GrExpression rExpr, GroovyPsiElement element) {
      if (PsiUtil.isRawClassMemberAccess(rExpr)) {
        final PsiType rType = rExpr.getType();
        if (!TypesUtil.isAssignable(lType, rType, element)) {
          registerError(element, lType, rType);
        }
      }
    }

  }
}
