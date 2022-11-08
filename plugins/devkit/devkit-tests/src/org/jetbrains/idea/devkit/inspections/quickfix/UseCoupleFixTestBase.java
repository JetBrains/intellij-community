// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.internal.UseCoupleInspection;

public abstract class UseCoupleFixTestBase extends JavaCodeInsightFixtureTestCase {

  protected static final String CONVERT_TO_COUPLE_OF_FIX_NAME = "Replace with 'Couple.of()'";
  protected static final String CONVERT_TO_COUPLE_TYPE_FIX_NAME = "Replace with 'Couple<String>'";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseCoupleInspection());
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
    moduleBuilder.addLibrary("platform-util-rt", PathUtil.getJarPathForClass(Pair.class));
  }

  @NotNull
  protected abstract String getFileExtension();

  protected void doTest(String fixName) {
    String testName = getTestName(false);
    String fileNameBefore = testName + '.' + getFileExtension();
    String fileNameAfter = testName + "_after." + getFileExtension();
    myFixture.testHighlighting(fileNameBefore);
    IntentionAction intention = myFixture.findSingleIntention(fixName);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResultByFile(fileNameBefore, fileNameAfter, true);
  }
}
