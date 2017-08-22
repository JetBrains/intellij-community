/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 * @since 10.01.2013
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
