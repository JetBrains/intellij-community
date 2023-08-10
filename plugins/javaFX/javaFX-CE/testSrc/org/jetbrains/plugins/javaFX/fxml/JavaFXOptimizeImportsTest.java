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

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

public class JavaFXOptimizeImportsTest extends AbstractJavaFXTestCase {
  public void testCollapseOnDemand() {
    doTest();
  }

  public void testRemoveUnused() {
    doTest();
  }

  public void testDblImports() {
    doTest();
  }

  public void testStaticPropertiesAttrAndCustomComponents() {
    myFixture.addClass("""
                         import javafx.scene.layout.GridPane;
                         public class MyGridPane extends GridPane {}
                         """);
    doTest();
  }

  public void testStaticPropertiesTagAndCustomComponents() {
    myFixture.addClass("""
                         import javafx.scene.layout.GridPane;
                         public class MyGridPane extends GridPane {}
                         """);
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".fxml");
    new OptimizeImportsProcessor(getProject(), myFixture.getFile()).run();
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/optimizeImports/";
  }
}
