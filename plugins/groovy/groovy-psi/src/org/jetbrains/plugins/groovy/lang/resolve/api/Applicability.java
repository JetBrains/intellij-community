// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.api;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public enum Applicability {
  applicable,
  canBeApplicable,
  inapplicable,
  ;

  public static @NotNull Applicability totalApplicability(@NotNull Collection<Applicability> applicabilities) {
    return applicabilities.isEmpty() ? applicable : Collections.max(applicabilities);
  }
}
