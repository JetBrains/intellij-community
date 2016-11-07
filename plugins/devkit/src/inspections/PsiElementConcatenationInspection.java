/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Tagir Valeev
 */
public class PsiElementConcatenationInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        String methodName = call.getMethodExpression().getReferenceName();
        if(methodName == null || !methodName.startsWith("create")) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if(args.length == 0) return;
        PsiExpression arg = args[0];
        PsiType argType = arg.getType();
        if(argType == null || !argType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return;
        PsiMethod method = call.resolveMethod();
        if(method == null) return;
        PsiClass aClass = method.getContainingClass();
        if(aClass == null || !PsiElementFactory.class.getName().equals(aClass.getQualifiedName())) return;
        checkOperand(arg, new HashSet<>());
      }

      @SuppressWarnings("DialogTitleCapitalization")
      private void checkOperand(@Nullable PsiExpression operand, Set<PsiExpression> visited) {
        if(operand == null || !visited.add(operand)) return;
        if(operand instanceof PsiReferenceExpression) {
          PsiElement element = ((PsiReferenceExpression)operand).resolve();
          if(element instanceof PsiLocalVariable && ((PsiLocalVariable)element).getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            PsiCodeBlock block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
            if(block != null) {
              PsiElement[] defs = DefUseUtil.getDefs(block, (PsiVariable)element, operand);
              for(PsiElement def : defs) {
                if(def instanceof PsiLocalVariable) {
                  checkOperand(((PsiLocalVariable)def).getInitializer(), visited);
                }
                if(def instanceof PsiReferenceExpression) {
                  PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(def.getParent());
                  if(assignment != null && assignment.getLExpression() == def) {
                    checkOperand(assignment.getRExpression(), visited);
                  }
                }
                if(def instanceof PsiExpression) {
                  checkOperand((PsiExpression)def, visited);
                }
              }
            }
          }
        }
        if(operand instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)operand;
          PsiMethod method = call.resolveMethod();
          if(MethodUtils.isToString(method)) {
            checkOperand(call.getMethodExpression().getQualifierExpression(), visited);
          }
        }
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(operand.getType());
        if(InheritanceUtil.isInheritor(aClass, false, PsiElement.class.getName())) {
          holder.registerProblem(operand, "Suspicious conversion of PsiElement to string",
                                 new AddGetTextFix("getText"));
        }
        if(InheritanceUtil.isInheritor(aClass, false, PsiType.class.getName())) {
          holder.registerProblem(operand, "Suspicious conversion of PsiType to string",
                                 new AddGetTextFix("getCanonicalText"));
        }
        if(operand instanceof PsiPolyadicExpression) {
          PsiPolyadicExpression polyadic = (PsiPolyadicExpression)operand;
          if(JavaTokenType.PLUS.equals(polyadic.getOperationTokenType())) {
            for (PsiExpression op : polyadic.getOperands()) {
              checkOperand(op, visited);
            }
          }
        }
      }
    };
  }

  private static class AddGetTextFix implements LocalQuickFix {
    private final String myMethodName;

    private AddGetTextFix(String name) {
      myMethodName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Use '"+myMethodName+"' call";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Call text representation retrieval method";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if(!(element instanceof PsiExpression)) return;
      PsiExpression expression = (PsiExpression)element;
      if(!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression replacement = factory.createExpressionFromText(ParenthesesUtils.getText(expression, ParenthesesUtils.POSTFIX_PRECEDENCE)
                                                                   + "." + myMethodName + "()", expression);
      PsiElement parent = expression.getParent().getParent();
      if(parent instanceof PsiMethodCallExpression && MethodUtils.isToString(((PsiMethodCallExpression)parent).resolveMethod())) {
        element = parent;
      }
      PsiElement result = element.replace(replacement);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }
}
