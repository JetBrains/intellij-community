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

package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
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
  public boolean isEnabledByDefault() {
    return true;
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
          final GrParametersOwner owner = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class, GrClosableBlock.class);
          if (owner instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod)owner;
            if (!method.isConstructor()) {
              final PsiType methodType = method.getReturnType();
              final PsiType returnType = value.getType();
              if (methodType != null) {
                if (!PsiType.VOID.equals(methodType)) {
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

      GrExpression lValue = assignment.getLValue();
      if (!PsiUtil.mightBeLValue(lValue)) return;

      IElementType opToken = assignment.getOperationTokenType();
      if (opToken != GroovyTokenTypes.mASSIGN) return;

      GrExpression rValue = assignment.getRValue();
      if (rValue == null) return;

      PsiType lType = lValue.getNominalType();
      PsiType rType = rValue.getType();

      // For assignments with spread dot
      if (PsiImplUtil.isSpreadAssignment(lValue) && lType != null && lType instanceof PsiClassType) {
        final PsiClassType pct = (PsiClassType)lType;
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
