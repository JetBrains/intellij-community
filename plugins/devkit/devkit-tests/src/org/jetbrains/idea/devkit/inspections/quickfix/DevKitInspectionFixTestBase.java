// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

public abstract class DevKitInspectionFixTestBase extends JavaCodeInsightFixtureTestCase {

  @NotNull
  protected abstract String getFileExtension();

  protected void doTest(String fixName) {
    String testName = getTestName(false);
    String fileNameBefore = testName + '.' + getFileExtension();
    String fileNameAfter = testName + "_after." + getFileExtension();
    myFixture.testHighlighting(fileNameBefore);
    IntentionAction intention = myFixture.findSingleIntention(fixName);
    Path previewPath = Path.of(myFixture.getTestDataPath(), testName + "_preview." + getFileExtension());
    if (Files.exists(previewPath)) {
      String previewText = myFixture.getIntentionPreviewText(intention);
      assertSameLinesWithFile(previewPath.toString(), previewText);
      myFixture.launchAction(intention);
    } else {
      myFixture.checkPreviewAndLaunchAction(intention);
    }
    myFixture.checkResultByFile(fileNameBefore, fileNameAfter, true);
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }
}
