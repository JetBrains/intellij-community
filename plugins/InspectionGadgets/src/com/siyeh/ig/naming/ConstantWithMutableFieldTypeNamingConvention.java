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

import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ClassUtils;

public class ConstantWithMutableFieldTypeNamingConvention extends NamingConvention<PsiField> {

  static final String CONSTANT_WITH_MUTABLE_FIELD_TYPE_NAMING_CONVENTION_SHORT_NAME = "ConstantWithMutableFieldTypeNamingConvention";

  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 32;


  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("constant.with.mutable.field.naming.convention.element.description");
  }

  @Override
  public String getShortName() {
    return CONSTANT_WITH_MUTABLE_FIELD_TYPE_NAMING_CONVENTION_SHORT_NAME;
  }

  @Override
  public NamingConventionBean createDefaultBean() {
    return new FieldNamingConventionInspection.FieldNamingConventionBean("[A-Z][A-Z_\\d]*", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
  }


  @Override
  public boolean isApplicable(PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
      if (Mutability.getMutability(field).isUnmodifiable()) {
        return false;
      }
      if (!ClassUtils.isImmutable(field.getType())) {
        return true;
      }
    }
    return false;
  }
}
