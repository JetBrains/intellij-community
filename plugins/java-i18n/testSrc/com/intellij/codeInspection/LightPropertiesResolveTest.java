// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightPropertiesResolveTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/lightResolve";
  }

  public void testSameNameProperty() {
    createBundles();
    PsiReference reference = myFixture.getReferenceAtCaretPosition("SameNameProperty.java");
    assertNotNull(reference.resolve());
  }

  private void createBundles() {
    myFixture.addFileToProject("Bundle1.properties", "same.name=b1");
    myFixture.addFileToProject("Bundle2.properties", "same.name=b2");
  }

  public void testLocalVar() {
    createBundles();
    PsiReference reference = myFixture.getReferenceAtCaretPosition("LocalVar.java");
    assertNotNull(reference.resolve());
  }

  public void testTypeUse() {
    createBundles();
    PsiReference reference = myFixture.getReferenceAtCaretPosition("TypeUse.java");
    assertNotNull(reference.resolve());
  }
}
