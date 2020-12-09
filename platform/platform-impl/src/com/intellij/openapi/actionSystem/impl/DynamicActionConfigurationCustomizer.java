// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import org.jetbrains.annotations.NotNull;

public interface DynamicActionConfigurationCustomizer {
  void registerActions(@NotNull ActionManager actionManager);
  void unregisterActions(@NotNull ActionManager actionManager);
}
