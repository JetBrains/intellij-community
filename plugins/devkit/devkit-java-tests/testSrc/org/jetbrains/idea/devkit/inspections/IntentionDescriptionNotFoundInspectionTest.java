// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

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
    moduleBuilder.addLibrary("platform-rt", PathUtil.getJarPathForClass(IncorrectOperationException.class));
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(Iconable.class));
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

  public void testNoHighlightingModCommand() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");
    myFixture.testHighlighting("MyModCommandIntentionWithDescription.java");
  }

  public void testHighlightingForBeforeAfter() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");
    myFixture.testHighlighting("MyIntentionActionWithoutBeforeAfter.java");
  }

  public void testHighlightingOptionalBeforeAfter() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");
    myFixture.testHighlighting("MyIntentionActionOptionalBeforeAfter.java");
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
