// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.ContextualLabel;
import org.jetbrains.annotations.NotNull;

public class InternalContextualLabel implements ContextualLabel {

  private final @NotNull String contextualLabel;

  public InternalContextualLabel(@NotNull String contextualLabel) {
    this.contextualLabel = contextualLabel;
  }

  @Override
  public @NotNull String getContextualLabel() {
    return contextualLabel;
  }
}
