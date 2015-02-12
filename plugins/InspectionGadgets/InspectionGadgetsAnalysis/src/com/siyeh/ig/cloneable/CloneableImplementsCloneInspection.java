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
package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CloneableImplementsCloneInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreCloneableDueToInheritance = false;

  @Override
  @NotNull
  public String getID() {
    return "CloneableClassWithoutClone";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("cloneable.class.without.clone.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("cloneable.class.without.clone.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "cloneable.class.without.clone.ignore.option"), this, "m_ignoreCloneableDueToInheritance");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new CreateCloneMethodFix();
  }

  private static class CreateCloneMethodFix extends InspectionGadgetsFix {
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("cloneable.class.without.clone.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final String cloneMethod =
        "public " + element.getText() + " clone() throws java.lang.CloneNotSupportedException {\n" +
        "return (" + element.getText() + ") super.clone();\n" +
        "}";
      final PsiMethod method = factory.createMethodFromText(cloneMethod, element);
      parent.add(method);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneableImplementsCloneVisitor();
  }

  private class CloneableImplementsCloneVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (m_ignoreCloneableDueToInheritance) {
        if (!CloneUtils.isDirectlyCloneable(aClass)) {
          return;
        }
      }
      else if (!CloneUtils.isCloneable(aClass)) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (CloneUtils.isClone(method)) {
          return;
        }
      }
      registerClassError(aClass);
    }
  }
}