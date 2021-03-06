// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

public class DumbServiceAlternativeResolveTracker {
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
