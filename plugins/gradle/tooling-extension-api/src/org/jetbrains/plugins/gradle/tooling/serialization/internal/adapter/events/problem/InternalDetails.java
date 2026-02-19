// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.Details;
import org.jetbrains.annotations.NotNull;

public class InternalDetails implements Details {

  private final @NotNull String details;

  public InternalDetails(@NotNull String details) {
    this.details = details;
  }

  @Override
  public @NotNull String getDetails() {
    return details;
  }
}
