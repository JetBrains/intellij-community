// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a provider for creating an instance of {@link SMTRunnerTestTreeView}.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface SMTRunnerTestTreeViewProvider {

  @NotNull
  SMTRunnerTestTreeView createSMTRunnerTestTreeView();

  interface CustomizedDurationProvider {

    /**
     * Retrieves the customized duration for a given test proxy.
     * Called from {@link SMTestProxy#getCustomizedDuration(TestConsoleProperties)}
     *
     * @param proxy the test proxy for which the customized duration is requested; must not be null
     * @return the customized duration in milliseconds, or null if no customized duration is provided
     */
    @Nullable
    Long getCustomizedDuration(@NotNull SMTestProxy proxy);
  }
}
