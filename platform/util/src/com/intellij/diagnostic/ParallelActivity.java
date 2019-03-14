// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;

public enum ParallelActivity {
  PREPARE_APP_INIT("prepareAppInitActivity"), PRELOAD_ACTIVITY("preloadActivity"),
  APP_OPTIONS_TOP_HIT_PROVIDER("appOptionsTopHitProvider"), PROJECT_OPTIONS_TOP_HIT_PROVIDER("projectOptionsTopHitProvider");

  private final String jsonName;

  ParallelActivity(@NotNull String jsonName) {
    this.jsonName = jsonName;
  }

  @NotNull
  public String getJsonName() {
    return jsonName;
  }

  @NotNull
  public Activity start(@NotNull String name) {
    return ActivityImpl.createParallelActivity(this, name);
  }

  public void record(long start, @NotNull Class<?> clazz) {
    long end = System.nanoTime();
    if ((end - start) <= StartUpMeasurer.MEASURE_THRESHOLD) {
      return;
    }

    ActivityImpl item = new ActivityImpl(clazz.getName(), /* description = */ null, start, /* parent = */ null, /* level = */ null, this);
    item.setEnd(end);
    StartUpMeasurer.add(item);
  }
}
