// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming;

import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TestUtils;

/**
 * @author Bas Leijdekkers
 */
public final class JUnit3MethodNamingConvention extends NamingConvention<PsiMethod> {

  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("junit3.method.naming.convention.element.description");
  }

  @Override
  public NamingConventionBean createDefaultBean() {
    return new NamingConventionBean("test[A-Za-z_\\d]*", 8, 64);
  }

  @Override
  public boolean isApplicable(PsiMethod method) {
    return TestUtils.isJUnit3TestMethod(method) && TestUtils.isRunnable(method);
  }

  @Override
  public String getShortName() {
    return "JUnit3MethodNamingConvention";
  }
}
