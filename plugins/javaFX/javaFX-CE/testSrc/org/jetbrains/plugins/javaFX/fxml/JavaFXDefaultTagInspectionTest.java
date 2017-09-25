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

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxDefaultTagInspection;

public class JavaFXDefaultTagInspectionTest extends AbstractJavaFXQuickFixTest {

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxDefaultTagInspection());
  }


  public void testChildren() {
    doLaunchQuickfixTest("children");
  }

  public void testEmptyChildren() {
    doLaunchQuickfixTest("children");
  }

  public void testFxCollectionsHighlighting() {
    doHighlightingTest();
  }

  public void testEmptyListHighlighting() {
    doHighlightingTest();
  }

  public void testEmptyCollapsedListHighlighting() {
    doHighlightingTest();
  }

  public void testStylesheets() {
    checkQuickFixNotAvailable("stylesheets");
  }

  private void doHighlightingTest() {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myFixture.checkHighlighting();
  }

  @Override
  protected String getHint(String tagName) {
    return "Unwrap '" + tagName + "'";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/defaultTag/";
  }
}
