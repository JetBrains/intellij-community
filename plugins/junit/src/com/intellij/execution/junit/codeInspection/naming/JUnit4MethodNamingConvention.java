// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.naming;

import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class JUnit4MethodNamingConvention extends NamingConvention<PsiMethod> {
  private static final @NonNls NamingConventionBean DEFAULT_BEAN = new NamingConventionBean("[a-z][A-Za-z_\\d]*", 4, 64);
  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("junit4.method.naming.convention.element.description");
  }

  @Override
  public boolean isApplicable(PsiMethod member) {
    return TestUtils.isExecutableTestMethod(member, List.of("JUnit4", "JUnit5", "JUnit6"));
  }

  @Override
  public String getShortName() {
    return "JUnit4MethodNamingConvention";
  }

  @Override
  public NamingConventionBean createDefaultBean() {
    return DEFAULT_BEAN;
  }
}
