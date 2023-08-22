// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prefer {@link LightDevKitInspectionFixTestBase} if possible.
 */
public abstract class DevKitInspectionFixTestBase extends JavaCodeInsightFixtureTestCase {

  @NotNull
  protected abstract String getFileExtension();

  protected void doTest(String fixName) {
    doTest(myFixture, fixName, getFileExtension(), getTestName(false));
  }

  static void doTest(JavaCodeInsightTestFixture fixture, String fixName, String fileExtension, String testName) {
    String fileNameBefore = testName + '.' + fileExtension;
    String fileNameAfter = testName + "_after." + fileExtension;
    fixture.testHighlighting(fileNameBefore);
    IntentionAction intention = fixture.findSingleIntention(fixName);
    Path previewPath = Path.of(fixture.getTestDataPath(), testName + "_preview." + fileExtension);
    if (Files.exists(previewPath)) {
      String previewText = fixture.getIntentionPreviewText(intention);
      assertSameLinesWithFile(previewPath.toString(), previewText);
      fixture.launchAction(intention);
    }
    else {
      fixture.checkPreviewAndLaunchAction(intention);
    }
    fixture.checkResultByFile(fileNameBefore, fileNameAfter, true);
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }
}
