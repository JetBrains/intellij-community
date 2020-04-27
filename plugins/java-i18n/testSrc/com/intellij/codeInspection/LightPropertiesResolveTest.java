// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class LightPropertiesResolveTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/lightResolve";
  }

  public void testSameNameProperty() {
    myFixture.addFileToProject("Bundle1.properties", "same.name=b1");
    myFixture.addFileToProject("Bundle2.properties", "same.name=b2");
    PsiReference reference = myFixture.getReferenceAtCaretPosition("SameNameProperty.java");
    assertNotNull(reference.resolve());
  }
}
