// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public interface ModelBuilderContext {

  @NotNull Gradle getGradle();

  @NotNull MessageReporter getMessageReporter();

  /**
   * @return cached data if it's already created, newly created data otherwise
   */
  @NotNull
  <T> T getData(@NotNull DataProvider<T> provider);

  interface DataProvider<T> {
    /**
     * Create data value to be shared.
     * Returned value should be thread-safe.
     */
    @NotNull
    T create(@NotNull ModelBuilderContext context);
  }
}
