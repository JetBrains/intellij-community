// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * ReportingClassSubstitutor is suitable in cases when reporting (e.g. FUS or logging)
 * has to be performed on behalf of another class. Can be implemented by common delegates or
 * wrappers to provide the original wrapped class to the logging/reporting code.
 */
@ApiStatus.Experimental
public interface ReportingClassSubstitutor {
  /**
   * @return the class which should be used to log or collect statistics. 
   */
  @NotNull Class<?> getSubstitutedClass();

  static @NotNull Class<?> getClassToReport(@NotNull Object object) {
    return object instanceof ReportingClassSubstitutor ? ((ReportingClassSubstitutor)object).getSubstitutedClass() : object.getClass();
  }
}
