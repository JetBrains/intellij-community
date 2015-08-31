/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TailRecursionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("tail.recursion.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "tail.recursion.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod containingMethod = (PsiMethod)infos[0];
    if (!mayBeReplacedByIterativeMethod(containingMethod)) {
      return null;
    }
    return new RemoveTailRecursionFix();
  }

  private static boolean mayBeReplacedByIterativeMethod(PsiMethod containingMethod) {
    final PsiParameterList parameterList = containingMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    for (final PsiParameter parameter : parameters) {
      if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
    }
    return true;
  }

  private static class RemoveTailRecursionFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "tail.recursion.replace.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement tailCallToken = descriptor.getPsiElement();
      final PsiMethod method = PsiTreeUtil.getParentOfType(tailCallToken, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (method == null) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      @NonNls final StringBuilder builder = new StringBuilder();
      builder.append('{');
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String thisVariableName;
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      if (methodReturnsContainingClassType(method, containingClass)) {
        builder.append(containingClass.getName());
        thisVariableName = styleManager.suggestUniqueVariableName("result", method, false);
        builder.append(' ');
        builder.append(thisVariableName);
        builder.append(" = this;");
      }
      else if (methodContainsCallOnOtherInstance(method)) {
        builder.append(containingClass.getName());
        thisVariableName = styleManager.suggestUniqueVariableName("other", method, false);
        builder.append(' ');
        builder.append(thisVariableName);
        builder.append(" = this;");
      }
      else {
        thisVariableName = null;
      }
      final boolean tailCallIsContainedInLoop;
      if (ControlFlowUtils.isInLoop(tailCallToken)) {
        tailCallIsContainedInLoop = true;
        builder.append(method.getName());
        builder.append(':');
      }
      else {
        tailCallIsContainedInLoop = false;
      }
      builder.append("while(true)");
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      replaceTailCalls(body, method, thisVariableName, tailCallIsContainedInLoop, builder);
      builder.append('}');
      @NonNls final String replacementText = builder.toString();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory elementFactory = psiFacade.getElementFactory();
      final PsiCodeBlock block = elementFactory.createCodeBlockFromText(replacementText, method);
      body.replace(block);
      codeStyleManager.reformat(method);
    }

    private static boolean methodReturnsContainingClassType(PsiMethod method, PsiClass containingClass) {
      if (containingClass == null) {
        return false;
      }
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      final PsiType returnType = method.getReturnType();
      if (!(returnType instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)returnType;
      final PsiClass aClass = classType.resolve();
      return containingClass.equals(aClass);
    }

    private static boolean methodContainsCallOnOtherInstance(PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      final MethodContainsCallOnOtherInstanceVisitor visitor = new MethodContainsCallOnOtherInstanceVisitor(aClass);
      body.accept(visitor);
      return visitor.containsCallOnOtherInstance();
    }

    private static class MethodContainsCallOnOtherInstanceVisitor extends JavaRecursiveElementWalkingVisitor {

      private boolean containsCallOnOtherInstance;
      private final PsiClass aClass;

      private MethodContainsCallOnOtherInstanceVisitor(PsiClass aClass) {
        this.aClass = aClass;
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (containsCallOnOtherInstance) {
          return;
        }
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
          return;
        }
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (aClass.equals(containingClass)) {
          containsCallOnOtherInstance = true;
        }
      }

      private boolean containsCallOnOtherInstance() {
        return containsCallOnOtherInstance;
      }
    }

    private static void replaceTailCalls(PsiElement element, PsiMethod method, @Nullable String thisVariableName, 
                                         boolean tailCallIsContainedInLoop, @NonNls StringBuilder out) {
      final String text = element.getText();
      if (isImplicitCallOnThis(element, method)) {
        if (thisVariableName != null) {
          out.append(thisVariableName);
          out.append('.');
        }
        out.append(text);
      }
      else if (element instanceof PsiThisExpression ||
               element instanceof PsiSuperExpression) {
        if (thisVariableName == null) {
          out.append(text);
        }
        else {
          out.append(thisVariableName);
        }
      }
      else if (isTailCallReturn(element, method)) {
        final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
        final PsiMethodCallExpression call = (PsiMethodCallExpression)returnStatement.getReturnValue();
        assert call != null;
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        final boolean isInBlock = returnStatement.getParent() instanceof PsiCodeBlock;
        if (!isInBlock) {
          out.append('{');
        }
        for (int i = 0; i < parameters.length; i++) {
          final PsiParameter parameter = parameters[i];
          final PsiExpression argument = arguments[i];
          final String parameterName = parameter.getName();
          if (parameterName == null) {
            continue;
          }
          final String argumentText = argument.getText();
          if (parameterName.equals(argumentText)) {
            continue;
          }
          out.append(parameterName);
          out.append(" = ");
          out.append(argumentText);
          out.append(';');
        }
        if (thisVariableName != null) {
          final PsiReferenceExpression methodExpression = call.getMethodExpression();
          final PsiExpression qualifier = methodExpression.getQualifierExpression();
          if (qualifier != null) {
            out.append(thisVariableName);
            out.append(" = ");
            replaceTailCalls(qualifier, method, thisVariableName, tailCallIsContainedInLoop, out);
            out.append(';');
          }
        }
        final PsiCodeBlock body = method.getBody();
        assert body != null;
        if (ControlFlowUtils.blockCompletesWithStatement(body, returnStatement)) {
          //don't do anything, as the continue is unnecessary
        }
        else if (tailCallIsContainedInLoop) {
          final String methodName = method.getName();
          out.append("continue ");
          out.append(methodName);
          out.append(';');
        }
        else {
          out.append("continue;");
        }
        if (!isInBlock) {
          out.append('}');
        }
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          out.append(text);
        }
        else {
          for (final PsiElement child : children) {
            replaceTailCalls(child, method, thisVariableName, tailCallIsContainedInLoop, out);
          }
        }
      }
    }

    private static boolean isImplicitCallOnThis(PsiElement element,
                                                PsiMethod containingMethod) {
      if (containingMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        return qualifierExpression == null;
      }
      else if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        final PsiElement parent = referenceExpression.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          return false;
        }
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier != null) {
          return false;
        }
        final PsiElement target = referenceExpression.resolve();
        return target instanceof PsiField;
      }
      else {
        return false;
      }
    }

    private static boolean isTailCallReturn(PsiElement element,
                                            PsiMethod containingMethod) {
      if (!(element instanceof PsiReturnStatement)) {
        return false;
      }
      final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (!(returnValue instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression call = (PsiMethodCallExpression)returnValue;
      final PsiMethod method = call.resolveMethod();
      return containingMethod.equals(method);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TailRecursionVisitor();
  }

  private static class TailRecursionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression returnValue = statement.getReturnValue();
      if (!(returnValue instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression returnCall = (PsiMethodCallExpression)returnValue;
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (containingMethod == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = returnCall.getMethodExpression();
      final String name = containingMethod.getName();
      if (!name.equals(methodExpression.getReferenceName())) {
        return;
      }
      final PsiMethod method = returnCall.resolveMethod();
      if (method == null) {
        return;
      }
      if (!method.equals(containingMethod)) {
        return;
      }
      registerMethodCallError(returnCall, containingMethod);
    }
  }
}