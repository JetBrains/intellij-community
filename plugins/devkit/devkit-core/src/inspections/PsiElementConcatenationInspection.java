// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.*;
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
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.HashSet;
import java.util.Set;

public class PsiElementConcatenationInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!DevKitInspectionUtil.isAllowed(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    if (!hasJavaPsiApi(holder)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        String methodName = call.getMethodExpression().getReferenceName();
        if (methodName == null || !methodName.startsWith("create")) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0) return;
        PsiExpression arg = args[0];
        PsiType argType = arg.getType();
        if (argType == null || !argType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return;
        PsiMethod method = call.resolveMethod();
        if (method == null) return;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null || !PsiElementFactory.class.getName().equals(aClass.getQualifiedName())) return;
        checkOperand(arg, new HashSet<>());
      }

      private void checkOperand(@Nullable PsiExpression operand, Set<PsiExpression> visited) {
        if (operand == null || !visited.add(operand)) return;
        if (operand instanceof PsiReferenceExpression) {
          PsiElement element = ((PsiReferenceExpression)operand).resolve();
          if (element instanceof PsiLocalVariable &&
              ((PsiLocalVariable)element).getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            PsiCodeBlock block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
            if (block != null) {
              PsiElement[] defs = DefUseUtil.getDefs(block, (PsiVariable)element, operand);
              for (PsiElement def : defs) {
                if (def instanceof PsiLocalVariable) {
                  checkOperand(((PsiLocalVariable)def).getInitializer(), visited);
                }
                if (def instanceof PsiReferenceExpression) {
                  PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(def.getParent());
                  if (assignment != null && assignment.getLExpression() == def) {
                    checkOperand(assignment.getRExpression(), visited);
                  }
                }
                if (def instanceof PsiExpression) {
                  checkOperand((PsiExpression)def, visited);
                }
              }
            }
          }
        }
        if (operand instanceof PsiMethodCallExpression call) {
          PsiMethod method = call.resolveMethod();
          if (MethodUtils.isToString(method)) {
            checkOperand(call.getMethodExpression().getQualifierExpression(), visited);
          }
        }
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(operand.getType());
        if (InheritanceUtil.isInheritor(aClass, false, PsiElement.class.getName())) {
          holder.registerProblem(operand, DevKitBundle.message("inspections.psi.element.concat.psi.element"),
                                 new AddGetTextFix("getText"));
        }
        if (InheritanceUtil.isInheritor(aClass, false, PsiType.class.getName())) {
          holder.registerProblem(operand, DevKitBundle.message("inspections.psi.element.concat.psi.type"),
                                 new AddGetTextFix("getCanonicalText"));
        }
        if (operand instanceof PsiPolyadicExpression polyadic) {
          if (JavaTokenType.PLUS.equals(polyadic.getOperationTokenType())) {
            for (PsiExpression op : polyadic.getOperands()) {
              checkOperand(op, visited);
            }
          }
        }
      }
    };
  }

  private static boolean hasJavaPsiApi(@NotNull ProblemsHolder holder) {
    return JavaPsiFacade.getInstance(holder.getProject())
             .findClass(PsiElementFactory.class.getName(), holder.getFile().getResolveScope()) != null;
  }

  private static final class AddGetTextFix implements LocalQuickFix {
    private final String myMethodName;

    private AddGetTextFix(String name) {
      myMethodName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return DevKitBundle.message("inspections.psi.element.concat.add.get.text.name", myMethodName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return DevKitBundle.message("inspections.psi.element.concat.add.get.text.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiExpression expression)) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression replacement = factory.createExpressionFromText(ParenthesesUtils.getText(expression, ParenthesesUtils.POSTFIX_PRECEDENCE)
                                                                   + "." + myMethodName + "()", expression);
      PsiElement parent = expression.getParent().getParent();
      if (parent instanceof PsiMethodCallExpression && MethodUtils.isToString(((PsiMethodCallExpression)parent).resolveMethod())) {
        element = parent;
      }
      PsiElement result = element.replace(replacement);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }
}
