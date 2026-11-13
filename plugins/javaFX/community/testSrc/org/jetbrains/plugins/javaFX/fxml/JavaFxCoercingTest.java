// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;

/**
 * @author anna
 */
@TestApplication
public class JavaFxCoercingTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/coercing/",
                  () -> List.of(new XmlPathReferenceInspection(),
                                new RequiredAttributesInspection(),
                                new UnusedDeclarationInspection()));

  @Test
  public void testReferencedTag() {
    doTest();
  }

  @Test
  public void testInvalidInteger() {
    doTest();
  }

  @Test
  public void testInvalidDouble() {
    doTest();
  }

  @Test
  public void testRootTagSubtagsCoercing() {
    doTest();
  }

  @Test
  public void testFactoryCoercing() {
    doTest();
  }

  @Test
  public void testPrimitiveCoercing() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.testHighlighting(false, false, false, myFixture.getTestName(true) + ".fxml");
  }
}
