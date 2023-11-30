/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.coverage;

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