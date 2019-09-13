// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;

// non-sequential and repeated items
public enum ParallelActivity {
  MAIN("item"),
  APP_INIT("prepareAppInitActivity"), PRELOAD_ACTIVITY("preloadActivity"),
  APP_OPTIONS_TOP_HIT_PROVIDER("appOptionsTopHitProvider"), PROJECT_OPTIONS_TOP_HIT_PROVIDER("projectOptionsTopHitProvider"),
  COMPONENT("component"), SERVICE("service"), EXTENSION("extension"),
  PROJECT_OPEN_HANDLER("openHandler"),

  POST_STARTUP_ACTIVITY("projectPostStartupActivity"),
  GC("GC"),
  REOPENING_EDITOR("reopeningEditor")
  ;

  private final String jsonName;

  ParallelActivity(@NotNull String jsonName) {
    this.jsonName = jsonName;
  }

  @NotNull
  public String getJsonName() {
    return jsonName;
  }
}
