// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.idea.devkit.inspections.internal.AbstractUseJBColorTestBase;

public abstract class UseJBColorFixTestBase extends AbstractUseJBColorTestBase {

  protected static final String CONVERT_TO_JB_COLOR_FIX_NAME = "Convert to 'JBColor'";
  protected static final String CONVERT_TO_JB_COLOR_CONSTANT_FIX_NAME = "Convert to 'JBColor' constant";

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
