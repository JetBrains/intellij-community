// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.stepping;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface ForceSmartStepIntoSource {
  /** Whether smart step into should be used even for one-variant code */
  default boolean needForceSmartStepInto() {
    return false;
  }
}
