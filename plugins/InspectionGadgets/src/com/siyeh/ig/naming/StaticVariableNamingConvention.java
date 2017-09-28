/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ClassUtils;

import javax.swing.*;
import java.awt.*;

public class StaticVariableNamingConvention extends NamingConvention<PsiField> {

  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Override
  public NamingConventionBean createDefaultBean() {
    return new ConstantNamingConventionBean();
  }

  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("static.variable.naming.convention.element.description");
  }

  @Override
  public String getShortName() {
    return "StaticVariableNamingConvention";
  }

  @Override
  public boolean isApplicable(PsiField field) {
    return field.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public boolean isValid(PsiField field, NamingConventionBean bean) {
    if (field.hasModifierProperty(PsiModifier.FINAL)) {
      if (((ConstantNamingConventionBean)bean).checkMutableFinals) {
        final PsiType type = field.getType();
        if (ClassUtils.isImmutable(type)) {
          return true;
        }
      }
      else {
        return true;
      }
    }
    return super.isValid(field, bean);
  }

  private static class ConstantNamingConventionBean extends FieldNamingConventionInspection.FieldNamingConventionBean {
    @SuppressWarnings({"PublicField"})
    public boolean checkMutableFinals = false;

    public ConstantNamingConventionBean() {
      super("[A-Z][A-Z_\\d]*", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
    }

    @Override
    public JComponent createOptionsPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      JComponent selfOptions = super.createOptionsPanel();
      JCheckBox inheritCb = new CheckBox(InspectionGadgetsBundle.message("static.variable.naming.convention.mutable.option"), this, "checkMutableFinals");
      panel.add(inheritCb, BorderLayout.NORTH);
      panel.add(selfOptions, BorderLayout.CENTER);
      return panel;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ConstantNamingConventionBean)) return false;
      if (!super.equals(o)) return false;

      ConstantNamingConventionBean bean = (ConstantNamingConventionBean)o;

      if (checkMutableFinals != bean.checkMutableFinals) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 31 * super.hashCode() + (checkMutableFinals ? 1 : 0);
    }
  }
}