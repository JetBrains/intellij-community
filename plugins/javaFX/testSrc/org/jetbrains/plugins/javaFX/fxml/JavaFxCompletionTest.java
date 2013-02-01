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

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 1/17/13
 */
public class JavaFxCompletionTest extends CompletionTestCase {

  @Override
  protected void setUpModule() {
    super.setUpModule();
    PsiTestUtil.addLibrary(getModule(), "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "jfxrt.jar");
  }

  public void testAvailablePositions() throws Exception {
    doTest();
  }

  public void testStaticProperties() throws Exception {
    doTest("GridPane.columnIndex");
  }
  
  public void testStaticPropertiesTag() throws Exception {
    doTest("GridPane.columnIndex");
  }

  public void testSimplePropertyTag() throws Exception {
    doTest("text");
  }

  public void testListPropertyTag() throws Exception {
    doTest("children");
  }

  public void testClassInsideList() throws Exception {
    doTest("Button");
  }

  public void testClassDefaultProperty() throws Exception {
    doTest("Button");
  }

  public void testClassInsertImport() throws Exception {
    doTest("Button");
  }

  public void testDefaultProperty() throws Exception {
    doTest("fx:id");
  }

  public void testDefaultTag() throws Exception {
    doTest("fx:script");
  }

  public void testStaticPropertiesEnumValue() throws Exception {
    doTest();
  }

  public void testConstants() throws Exception {
    doTest("NEGATIVE_INFINITY");
  }

  public void testReferencedAttributes() throws Exception {
    doTest("text");
  }

  public void testFactoryMethods() throws Exception {
    doTest("observableArrayList");
  }

  private void doTest() throws Exception {
    doTest(null);
  }

  private void doTest(final String selection) throws Exception {
    configureByFile(getTestName(true) + ".fxml");
    assertTrue(myItems.length > 0);
    LookupElement selectionElement = null;
    for (LookupElement item : myItems) {
      if (item.getLookupString().equals(selection)) {
        selectionElement = item;
        break;
      }
    }
    if (selection != null && selectionElement == null) {
      fail(selection + " is not suggested");
    }
    if (selectionElement == null) {
      selectionElement = myItems[0];
    }
    selectItem(selectionElement);
    checkResultByFile(getTestName(true) + "_after.fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/completion/";
  }
}
