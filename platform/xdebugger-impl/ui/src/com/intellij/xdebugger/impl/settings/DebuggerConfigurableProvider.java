// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public final class DebuggerConfigurableProvider extends ConfigurableProvider implements Configurable.WithEpDependencies {
  @Override
  public @NotNull Configurable createConfigurable() {
    return new DebuggerConfigurable();
  }

  @Override
  public boolean canCreateConfigurable() {
    return XBreakpointType.EXTENSION_POINT_NAME.hasAnyExtensions();
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return List.of(XBreakpointType.EXTENSION_POINT_NAME, com.intellij.xdebugger.settings.DebuggerConfigurableProvider.EXTENSION_POINT);
  }
}
