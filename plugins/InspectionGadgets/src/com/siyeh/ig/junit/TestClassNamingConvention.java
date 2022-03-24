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

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;

public class TestClassNamingConvention extends NamingConvention<PsiClass> {

  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 255;
  @NonNls public static final String TEST_CLASS_NAMING_CONVENTION_SHORT_NAME = "JUnitTestClassNamingConvention";

  @Override
  public NamingConventionBean createDefaultBean() {
    return new NamingConventionBean("[A-Z][A-Za-z\\d]*Test(s|Case)?|Test[A-Z][A-Za-z\\d]*|IT(.*)|(.*)IT(Case)?", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean isApplicable(PsiClass member) {
    if (member instanceof PsiTypeParameter) {
      return false;
    }
    if (!member.hasModifierProperty(PsiModifier.STATIC) && member.getContainingClass() != null) {
      return false;
    }
    TestFramework framework = TestFrameworks.detectFramework(member);
    return framework instanceof JavaTestFramework && framework.isTestClass(member) && !((JavaTestFramework)framework).isSuiteClass(member);
  }

  @Override
  public String getShortName() {
    return TEST_CLASS_NAMING_CONVENTION_SHORT_NAME;
  }

  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("junit.test.class.naming.convention.element.description");
  }
}