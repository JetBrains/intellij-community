// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

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
