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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SetupCallsSuperSetupInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "SetUpDoesntCallSuperSetUp";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "setup.calls.super.setup.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "setup.calls.super.setup.problem.descriptor");
  }

  private static class AddSuperSetUpCall extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "setup.calls.super.setup.add.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodName.getParent();
      assert method != null;
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiStatement newStatement =
        factory.createStatementFromText("super.setUp();", null);
      final CodeStyleManager styleManager =
        CodeStyleManager.getInstance(project);
      final PsiJavaToken brace = body.getLBrace();
      body.addAfter(newStatement, brace);
      styleManager.reformat(body);
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AddSuperSetUpCall();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SetupCallsSuperSetupVisitor();
  }

  private static class SetupCallsSuperSetupVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      @NonNls final String methodName = method.getName();
      if (!"setUp".equals(methodName)) {
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
      if (!InheritanceUtil.isInheritor(targetClass,
                                       "junit.framework.TestCase")) {
        return;
      }
      final CallToSuperSetupVisitor visitor =
        new CallToSuperSetupVisitor();
      method.accept(visitor);
      if (visitor.isCallToSuperSetupFound()) {
        return;
      }
      registerMethodError(method);
    }
  }
}