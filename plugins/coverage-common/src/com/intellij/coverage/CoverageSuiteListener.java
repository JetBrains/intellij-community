// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.coverage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Listener of process' coverage lifecycle
 */
public interface CoverageSuiteListener {
  /**
   * Called <b>once</b> when the coverage process has ended
   *
   * @param suite the new suite corresponding to this coverage
   */
  default void coverageGathered(@NotNull CoverageSuite suite) {}

  /**
   * Called <b>each time</b> before a coverage suite is opened, added to existing, selected, closed
   */
  default void beforeSuiteChosen() {}

  /**
   * Called <b>each time</b> after a coverage suite is opened, added to existing, selected, closed
   */
  default void afterSuiteChosen() {}


  /**
   * Called <b>each time</b> after a coverage suite is completely processed: data is loaded and accumulated
   */
  default void coverageDataCalculated(@NotNull CoverageSuitesBundle bundle) {}
}