/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAssignabilityCheckInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return ASSIGNMENT_ISSUES;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Incompatible Types Assignments Inspection";
  }

  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("cannot.assign", args);
  }

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    private void checkAssignability(@NotNull PsiType expectedType, @NotNull GrExpression expression, GroovyPsiElement element) {
      if (PsiUtil.isRawClassMemberAccess(expression)) return; //GRVY-2197
      final PsiType rType = expression.getType();
      if (rType == null || rType == PsiType.VOID) return;
      if (!TypesUtil.isAssignable(expectedType, rType, element)) {
        registerError(element, rType.getPresentableText(), expectedType.getPresentableText());
      }
    }

    //isApplicable last expression on method body
    @Override
    public void visitOpenBlock(GrOpenBlock block) {
      super.visitOpenBlock(block);
      final PsiElement element = block.getParent();
      if (!(element instanceof GrMethod)) return;
      GrMethod method = (GrMethod)element;
      final PsiType expectedType = method.getReturnType();
      if (expectedType == null || PsiType.VOID.equals(expectedType)) return;

      ControlFlowUtils.visitAllExitPoints(block, new ControlFlowUtils.ExitPointVisitor() {
        @Override
        public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
          if (returnValue != null && !(returnValue.getParent() instanceof GrReturnStatement)) {
            checkAssignability(expectedType, returnValue, returnValue);
          }
          return true;
        }
      });
    }

    @Override
    public void visitReturnStatement(GrReturnStatement returnStatement) {
      super.visitReturnStatement(returnStatement);

      final PsiElement parent = returnStatement.getParent();
      if (!(parent instanceof GrOpenBlock)) return;
      final PsiElement element = parent.getParent();
      if (!(element instanceof GrMethod)) return;
      GrMethod method = (GrMethod)element;

      final GrExpression value = returnStatement.getReturnValue();
      final PsiType expectedType = method.getReturnType();
      if (value == null || expectedType == null) return;
      checkAssignability(expectedType, value, returnStatement);
    }

    @Override
    public void visitNamedArgument(GrNamedArgument argument) {
      super.visitNamedArgument(argument);

      final GrArgumentLabel label = argument.getLabel();
      if (label == null) return;

      PsiType expectedType = label.getExpectedArgumentType();
      if (expectedType == null) return;

      expectedType = TypeConversionUtil.erasure(expectedType);
      final GrExpression expr = argument.getExpression();
      if (expr == null) return;

      final PsiType argType = expr.getType();
      if (argType == null) return;
      final PsiClassType listType = JavaPsiFacade.getInstance(argument.getProject()).getElementFactory()
        .createTypeByFQClassName(CommonClassNames.JAVA_UTIL_LIST, argument.getResolveScope());
      if (listType.isAssignableFrom(argType)) return; //this is constructor arguments list

      checkAssignability(expectedType, expr, argument);
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);

      GrExpression lValue = assignment.getLValue();
      if (!PsiUtil.mightBeLValue(lValue)) return;

      IElementType opToken = assignment.getOperationToken();
      if (opToken != GroovyTokenTypes.mASSIGN) return;

      GrExpression rValue = assignment.getRValue();
      if (rValue == null) return;

      PsiType lType = lValue.getNominalType();
      PsiType rType = rValue.getType();
      // For assignments with spread dot
      if (isListAssignment(lValue) && lType != null && lType instanceof PsiClassType) {
        final PsiClassType pct = (PsiClassType)lType;
        final PsiClass clazz = pct.resolve();
        if (clazz != null && CommonClassNames.JAVA_UTIL_LIST.equals(clazz.getQualifiedName())) {
          final PsiType[] types = pct.getParameters();
          if (types.length == 1 && types[0] != null && rType != null) {
            checkAssignability(types[0], rValue, rValue);
          }
        }
        return;
      }
      if (lValue instanceof GrReferenceExpression &&
          ((GrReferenceExpression)lValue).resolve() instanceof GrReferenceExpression) { //lvalue is not-declared variable
        return;
      }
      if (lType != null && rType != null) {
        checkAssignability(lType, rValue, rValue);
      }
    }

    @Override
    public void visitVariable(GrVariable variable) {
      super.visitVariable(variable);

      PsiType varType = variable.getType();
      GrExpression initializer = variable.getInitializerGroovy();
      if (initializer == null) return;

      PsiType rType = initializer.getType();
      if (rType == null) return;

      checkAssignability(varType, initializer, initializer);
    }
  }

  private static boolean isListAssignment(GrExpression lValue) {
    if (lValue instanceof GrReferenceExpression) {
      GrReferenceExpression expression = (GrReferenceExpression)lValue;
      final PsiElement dot = expression.getDotToken();
      //noinspection ConstantConditions
      if (dot != null && dot.getNode().getElementType() == GroovyTokenTypes.mSPREAD_DOT) {
        return true;
      }
      else {
        final GrExpression qual = expression.getQualifierExpression();
        if (qual != null) return isListAssignment(qual);
      }
    }
    return false;
  }


}
