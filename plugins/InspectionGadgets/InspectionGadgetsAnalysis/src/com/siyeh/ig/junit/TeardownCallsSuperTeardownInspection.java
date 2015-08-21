/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TeardownCallsSuperTeardownInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "TearDownDoesntCallSuperTearDown";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "teardown.calls.super.teardown.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "teardown.calls.super.teardown.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AddSuperTearDownCall();
  }

  private static class AddSuperTearDownCall extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "teardown.calls.super.teardown.add.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodName.getParent();
      if (method == null) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiStatement newStatement =
        factory.createStatementFromText("super.tearDown();", null);
      final CodeStyleManager styleManager =
        CodeStyleManager.getInstance(project);
      final PsiJavaToken brace = body.getRBrace();
      body.addBefore(newStatement, brace);
      styleManager.reformat(body);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TeardownCallsSuperTeardownVisitor("tearDown");
  }

  public static class TeardownCallsSuperTeardownVisitor extends BaseInspectionVisitor {

    private final String myMethodName;

    public TeardownCallsSuperTeardownVisitor(String methodName) {
      myMethodName = methodName;
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      @NonNls final String methodName = method.getName();
      if (!myMethodName.equals(methodName)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (method.getBody() == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      final PsiClass targetClass = method.getContainingClass();
      if (targetClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(targetClass, "junit.framework.TestCase")) {
        return;
      }
      if (MethodCallUtils.containsSuperMethodCall(myMethodName, method)) {
        return;
      }
      registerMethodError(method);
    }
  }
}