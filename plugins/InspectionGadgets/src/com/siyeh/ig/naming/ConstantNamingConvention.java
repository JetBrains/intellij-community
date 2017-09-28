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

public class ConstantNamingConvention extends NamingConvention<PsiField> {


  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 32;


  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("constant.naming.convention.element.description");
  }

  @Override
  public String getShortName() {
    return "ConstantNamingConvention";
  }

  @Override
  public NamingConventionBean createDefaultBean() {
    return new ConstantNamingConventionBean();
  }


  @Override
  public boolean isApplicable(PsiField field) {
    return field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL);
  }

  @Override
  public boolean isValid(PsiField member, NamingConventionBean bean) {
    if (((ConstantNamingConventionBean)bean).onlyCheckImmutables) {
      final PsiType type = member.getType();
      if (!ClassUtils.isImmutable(type)) {
        return true;
      }
    }
    return super.isValid(member, bean);
  }

  private static class ConstantNamingConventionBean extends FieldNamingConventionInspection.FieldNamingConventionBean {
    @SuppressWarnings({"PublicField"})
    public boolean onlyCheckImmutables = false;

    public ConstantNamingConventionBean() {
      super("[A-Z][A-Z_\\d]*", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
    }

    @Override
    public JComponent createOptionsPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      JComponent selfOptions = super.createOptionsPanel();
      JCheckBox inheritCb = new CheckBox(InspectionGadgetsBundle.message("constant.naming.convention.immutables.option"), this, "onlyCheckImmutables");
      panel.add(inheritCb, BorderLayout.NORTH);
      panel.add(selfOptions, BorderLayout.CENTER);
      return panel;
    }

  }
}
