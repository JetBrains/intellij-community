// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.internal.AbstractUseDPIAwareEmptyBorderTestBase;

public abstract class UseDPIAwareEmptyBorderFixTestBase extends AbstractUseDPIAwareEmptyBorderTestBase {

  @SuppressWarnings("SSBasedInspection")
  protected static final String SIMPLIFY_FIX_NAME =
    DevKitBundle.message("inspections.use.dpi.aware.empty.border.simplify.fix.name");
  @SuppressWarnings("SSBasedInspection")
  protected static final String CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME =
    DevKitBundle.message("inspections.use.dpi.aware.empty.border.convert.fix.name");

  protected void doTest(String fixName, String before, String after) {
    myFixture.configureByText("TestClass." + getFileExtension(), before);
    myFixture.checkHighlighting();
    IntentionAction intention = myFixture.findSingleIntention(fixName);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResult(after, true);
  }
}
