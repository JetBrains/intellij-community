// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Deprecated
public abstract class DebuggerSupport {
  private static final ExtensionPointName<DebuggerSupport> EXTENSION_POINT = new ExtensionPointName<>("com.intellij.xdebugger.debuggerSupport");

  @ApiStatus.Internal
  public static @NotNull List<@NotNull DebuggerSupport> getDebuggerSupports() {
    return EXTENSION_POINT.getExtensionList();
  }
}
