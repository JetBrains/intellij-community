// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback;

public final class StageInfo {
  
  private final String myName;

  public StageInfo(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }
}
