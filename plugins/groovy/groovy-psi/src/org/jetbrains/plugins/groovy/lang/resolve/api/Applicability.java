// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public enum Applicability {
  applicable,
  canBeApplicable,
  inapplicable,
  ;

  @NotNull
  public static Applicability totalApplicability(@NotNull Collection<Applicability> applicabilities) {
    return applicabilities.isEmpty() ? applicable : Collections.max(applicabilities);
  }
}
