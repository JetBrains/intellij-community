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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxUnresolvedFxIdReferenceInspection;

public class JavaFXQuickfixTest extends LightCodeInsightFixtureTestCase {
  public static final DefaultLightProjectDescriptor JAVA_FX_WITH_GROOVY_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
       public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
       PsiTestUtil.addLibrary(module, model, "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "jfxrt.jar");
       PsiTestUtil.addLibrary(module, model, "groovy", PluginPathManager.getPluginHomePath("groovy") + "/testdata/mockGroovyLib1.8", "groovy-1.8.0-beta-2.jar");
       super.configureModule(module, model, contentEntry);
     }
   };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_FX_WITH_GROOVY_DESCRIPTOR;
  }

  public void testCreateControllerMethod() throws Exception {
    doTest("Create Method 'void bar(ActionEvent)'", ".java");
  }

  public void testCreateControllerMethodInGroovy() throws Exception {
    doTest("Create Method 'void bar(ActionEvent)'", ".groovy");
  }

  public void testCreateField() throws Exception {
    doTest("Create Field 'btn'", ".java");
  }

  public void testCreateFieldEmptyName() throws Exception {
    String path = getTestName(true) + ".fxml";
    final IntentionAction intention =
      myFixture.getAvailableIntention("Create Field 'btn'", path, getTestName(false) + ".java");
    assertNull(intention);
  }

  public void testRegisterPageLanguage() throws Exception {
    myFixture.configureByFile(getTestName(true) + ".fxml");
    final IntentionAction intention = myFixture.findSingleIntention("Specify page language");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(true) + ".fxml", getTestName(true) + "_after.fxml", true);
  }

  public void testWrapWithDefine() throws Exception {
    final IntentionAction intention =
      myFixture.getAvailableIntention("Wrap \"lb\" with fx:define", getTestName(true) + ".fxml");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
  }

  private void doTest(final String actionName, final String extension) throws Exception {
    String path = getTestName(true) + ".fxml";
    final IntentionAction intention =
      myFixture.getAvailableIntention(actionName, path, getTestName(false) + extension);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + extension, getTestName(false) + "_after" + extension, true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JavaFxUnresolvedFxIdReferenceInspection());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/quickfix/";
  }
}
