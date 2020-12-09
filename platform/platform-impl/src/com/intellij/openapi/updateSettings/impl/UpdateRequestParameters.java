// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.util.Url;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public class UpdateRequestParameters {
  private static final Map<String, String> ourParameters = new HashMap<>();

  public static void addParameter(@NotNull String name, @NotNull String value) {
    synchronized (ourParameters) {
      ourParameters.put(name, value);
    }
  }

  static @NotNull Url amendUpdateRequest(@NotNull Url url) {
    synchronized (ourParameters) {
      return url.addParameters(ourParameters);
    }
  }
}
