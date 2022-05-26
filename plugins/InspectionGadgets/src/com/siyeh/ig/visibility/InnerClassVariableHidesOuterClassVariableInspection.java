/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InnerClassVariableHidesOuterClassVariableInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreInvisibleFields = true;

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @NotNull
  public String getID() {
    return "InnerClassFieldHidesOuterClassField";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "inner.class.field.hides.outer.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "inner.class.field.hides.outer.ignore.option"),
                                          this, "m_ignoreInvisibleFields");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassVariableHidesOuterClassVariableVisitor();
  }

  private class InnerClassVariableHidesOuterClassVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String fieldName = field.getName();
      if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(fieldName)) {
        return;    //special case
      }
      PsiClass ancestorClass = ClassUtils.getContainingClass(aClass);
      while (ancestorClass != null) {
        final PsiField ancestorField = ancestorClass.findFieldByName(fieldName, false);
        if (ancestorField != null) {
          if (!m_ignoreInvisibleFields
              || ancestorField.hasModifierProperty(PsiModifier.STATIC)
              || !CommonJavaRefactoringUtil.isInStaticContext(aClass, ancestorClass)) {
            registerFieldError(field);
          }
        }
        aClass = ancestorClass;
        ancestorClass = ClassUtils.getContainingClass(aClass);
      }
    }
  }
}