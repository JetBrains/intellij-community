// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class DumbServiceAlternativeResolveTracker {
  private final ThreadLocal<Integer> myAlternativeResolution = new ThreadLocal<>();

  boolean isAlternativeResolveEnabled() {
    return myAlternativeResolution.get() != null;
  }

  void setAlternativeResolveEnabled(boolean enabled) {
    Integer oldValue = myAlternativeResolution.get();
    int newValue = (oldValue == null ? 0 : oldValue) + (enabled ? 1 : -1);
    assert newValue >= 0 : "Non-paired alternative resolution mode";
    myAlternativeResolution.set(newValue == 0 ? null : newValue);
  }
}
