// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * ReportingClassSubstitutor is suitable in cases when reporting (e.g. FUS)
 * has to be performed on behalf of another class.
 */
@ApiStatus.Experimental
public interface ReportingClassSubstitutor {
  @NotNull Class<?> getSubstitutedClass();

  static @NotNull Class<?> getClassToReport(@NotNull Object object) {
    return object instanceof ReportingClassSubstitutor substitutor ? substitutor.getSubstitutedClass() : object.getClass();
  }
}
