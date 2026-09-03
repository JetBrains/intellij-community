// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util;

import com.intellij.groovy.testFramework.BaseTest;
import org.jetbrains.annotations.NotNull;

public interface ActionTest extends BaseTest {

  default void doActionTest(@NotNull String hint, @NotNull String before, @NotNull String after) {
    configureByText(before);
    doActionTest(hint, after);
  }

  default void doActionTest(@NotNull String hint, @NotNull String after) {
    getFixture().launchAction(getFixture().findSingleIntention(hint));
    getFixture().checkResult(after);
  }
}
