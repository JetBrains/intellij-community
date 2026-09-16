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

  /**
   * Returns the logic that computes a customized duration, or null if this provider gives none.
   * <p>
   * The test tree sort calls this method off the EDT and under a read action.
   * The method must not create a Swing component. A Swing component takes the AWT tree lock.
   * The read lock plus the AWT tree lock can make a deadlock. See IJPL-254402.
   *
   * @return the customized-duration logic, or null to use the default duration
   */
  default @Nullable CustomizedDurationProvider getCustomizedDurationProvider() {
    return null;
  }

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
