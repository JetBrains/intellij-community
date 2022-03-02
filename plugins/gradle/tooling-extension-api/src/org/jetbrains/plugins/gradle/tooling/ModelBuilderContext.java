// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface ModelBuilderContext extends MessageReporter {
  /**
   * @return root Gradle instance
   */
  @NotNull
  Gradle getRootGradle();

  /**
   * @return the value of the parameter passed to the builder, if the parametrized version of {@link BuildController#getModel} is used.
   */
  @Nullable
  String getParameter();

  /**
   * @return cached data if it's already created, newly created data otherwise
   */
  @NotNull
  <T> T getData(@NotNull DataProvider<T> provider);

  interface DataProvider<T> {
    /**
     * Create data value to be shared.
     * Returned value should be thread-safe.
     * @param gradle
     * @param messageReporter
     * @return
     */
    @NotNull
    T create(@NotNull Gradle gradle, @NotNull MessageReporter messageReporter);
  }
}
