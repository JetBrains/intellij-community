// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Use {@link DevKitInspectionFixTestBase} if light test is not possible.
 */
public abstract class LightDevKitInspectionFixTestBase extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  protected abstract String getFileExtension();

  protected void doTest(String fixName) {
    DevKitInspectionFixTestBase.doTest(myFixture, fixName, getFileExtension(), getTestName(false));
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }
}
