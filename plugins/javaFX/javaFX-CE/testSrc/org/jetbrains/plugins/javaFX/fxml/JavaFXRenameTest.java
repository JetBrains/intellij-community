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

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

public class JavaFXRenameTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    PsiTestUtil.addLibrary(getModule(), "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "jfxrt.jar");
  }

  public void testCustomComponent() throws Exception {
    doTest(getTestName(false) + "1");
  }

  public void testControllerField() throws Exception {
    doTest("newFieldName");
  }

  public void testHandler() throws Exception {
    doTest("newHandlerName");
  }

  public void testCustomComponentTag() throws Exception {
    doTest("Foo");
  }

  private void doTest(final String newName) throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase
      .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(getTestName(true) + "_after.fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/rename/";
  }
}
