/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TestUtils;

/**
 * @author Bas Leijdekkers
 */
public class JUnit4MethodNamingConvention extends NamingConvention<PsiMethod> {
  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("junit4.method.naming.convention.element.description");
  }

  @Override
  public boolean isApplicable(PsiMethod member) {
    return TestUtils.isAnnotatedTestMethod(member);
  }

  @Override
  public String getShortName() {
    return "JUnit4MethodNamingConvention";
  }

  @Override
  public NamingConventionBean createDefaultBean() {
    return new NamingConventionBean("[a-z][A-Za-z_\\d]*", 4, 64);
  }
}
