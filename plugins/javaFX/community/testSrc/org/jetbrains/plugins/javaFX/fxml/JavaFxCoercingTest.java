// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public class JavaFxCoercingTest extends AbstractJavaFXTestCase {

  public void testReferencedTag() {
    doTest();
  }

  public void testInvalidInteger() {
    doTest();
  }

  public void testInvalidDouble() {
    doTest();
  }

  public void testRootTagSubtagsCoercing() {
    doTest();
  }

  public void testFactoryCoercing() {
    doTest();
  }

  public void testPrimitiveCoercing() {
    doTest();
  }

  private void doTest() {
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
  }

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new XmlPathReferenceInspection(), 
                                new RequiredAttributesInspection(), 
                                new UnusedDeclarationInspection());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/coercing/";
  }
}
