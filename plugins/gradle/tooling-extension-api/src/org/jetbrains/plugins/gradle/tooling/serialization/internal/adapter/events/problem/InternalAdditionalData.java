// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.AdditionalData;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class InternalAdditionalData implements AdditionalData {

  private final @NotNull Map<String, Object> data;

  public InternalAdditionalData(@NotNull Map<String, Object> data) {
    this.data = data;
  }

  @Override
  public @NotNull Map<String, Object> getAsMap() {
    return data;
  }
}
