// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.telemetry;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class GradleTracingContext extends HashMap<String, String> {

  public static final @NotNull String REQUESTED_FORMAT_KEY = "REQUESTED_FORMAT";

  public static final TextMapGetter<GradleTracingContext> GETTER = new TextMapGetter<GradleTracingContext>() {
    @Override
    public Iterable<String> keys(@NotNull GradleTracingContext carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(@Nullable GradleTracingContext carrier, @NotNull String key) {
      if (carrier == null) {
        return null;
      }
      return carrier.get(key);
    }
  };

  public static final TextMapSetter<GradleTracingContext> SETTER = new TextMapSetter<GradleTracingContext>() {
    @Override
    public void set(@Nullable GradleTracingContext carrier, @NotNull String key, @NotNull String value) {
      if (carrier == null) {
        return;
      }
      carrier.put(key, value);
    }
  };
}
