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

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFXDefaultTagInspectionTest extends AbstractJavaFXQuickFixTest {

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxDefaultTagInspection());
  }


  public void testChildren() throws Exception {
    doLaunchQuickfixTest("children");
  }

  public void testEmptyChildren() throws Exception {
    doLaunchQuickfixTest("children");
  }

  public void testStylesheets() throws Exception {
    checkQuickFixNotAvailable("stylesheets");
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
