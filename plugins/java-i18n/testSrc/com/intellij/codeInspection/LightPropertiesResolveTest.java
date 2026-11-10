// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LightPropertiesResolveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/lightResolve");

  @Test
  public void testSameNameProperty() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    createBundles();
    PsiReference reference = myFixture.getReferenceAtCaretPosition("SameNameProperty.java");
    ReadAction.runBlocking(() -> assertNotNull(reference.resolve()));
  }

  private void createBundles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Bundle1.properties", "same.name=b1");
    myFixture.addFileToProject("Bundle2.properties", "same.name=b2");
  }

  @Test
  public void testLocalVar() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    createBundles();
    PsiReference reference = myFixture.getReferenceAtCaretPosition("LocalVar.java");
    ReadAction.runBlocking(() -> assertNotNull(reference.resolve()));
  }

  @Test
  public void testTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    createBundles();
    PsiReference reference = myFixture.getReferenceAtCaretPosition("TypeUse.java");
    ReadAction.runBlocking(() -> assertNotNull(reference.resolve()));
  }
}
