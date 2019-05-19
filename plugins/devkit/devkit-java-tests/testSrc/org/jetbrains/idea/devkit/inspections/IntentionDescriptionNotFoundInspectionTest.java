/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.nio.file.Paths;

@TestDataPath("$CONTENT_ROOT/testData/inspections/intentionDescription")
public class IntentionDescriptionNotFoundInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/intentionDescription";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("core", PathUtil.getJarPathForClass(Project.class));
    moduleBuilder.addLibrary("editor", PathUtil.getJarPathForClass(Editor.class));
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(IntentionActionBean.class));
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(IncorrectOperationException.class));
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP.class))
      .resolveSibling("intellij.platform.resources").toString());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(IntentionDescriptionNotFoundInspection.class);
    myFixture.copyDirectoryToProject("resources", "resources");
  }

  public void testHighlightingForDescription() {
    myFixture.testHighlighting("MyIntentionAction.java");
  }

  public void testHighlightingNotRegisteredInPluginXml() {
    myFixture.testHighlighting("MyIntentionActionNotRegisteredInPluginXml.java");
  }

  public void testNoHighlighting() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");
    myFixture.testHighlighting("MyIntentionActionWithDescription.java");
  }

  public void testHighlightingForBeforeAfter() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");
    myFixture.testHighlighting("MyIntentionActionWithoutBeforeAfter.java");
  }

  public void testQuickFix() {
    myFixture.configureByFile("MyQuickFixIntentionAction.java");
    IntentionAction item = myFixture.findSingleIntention("Create description file description.html");
    myFixture.launchAction(item);

    VirtualFile path = myFixture.findFileInTempDir("intentionDescriptions/MyQuickFixIntentionAction/description.html");
    assertNotNull(path);
    assertTrue(path.exists());
  }
}
