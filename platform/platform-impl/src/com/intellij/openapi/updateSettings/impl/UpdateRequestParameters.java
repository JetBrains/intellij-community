// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.util.Url;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class UpdateRequestParameters {
  private static final Map<String, String> ourParameters = new HashMap<>();

  public static void addParameter(@NotNull String name, @NotNull String value) {
    synchronized (ourParameters) {
      ourParameters.put(name, value);
    }
  }

  public static void removeParameter(@NotNull String name) {
    synchronized (ourParameters) {
      ourParameters.remove(name);
    }
  }

  @ApiStatus.Internal
  public static @NotNull Url amendUpdateRequest(@NotNull Url url) {
    synchronized (ourParameters) {
      return url.addParameters(ourParameters);
    }
  }
}
