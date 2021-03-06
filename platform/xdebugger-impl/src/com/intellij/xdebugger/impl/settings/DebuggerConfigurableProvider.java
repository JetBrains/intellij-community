// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class DebuggerConfigurableProvider extends ConfigurableProvider implements Configurable.WithEpDependencies {
  @NotNull
  @Override
  public Configurable createConfigurable() {
    return new DebuggerConfigurable();
  }

  @Override
  public boolean canCreateConfigurable() {
    return XBreakpointType.EXTENSION_POINT_NAME.hasAnyExtensions();
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return ContainerUtil.newArrayList(XBreakpointType.EXTENSION_POINT_NAME, com.intellij.xdebugger.settings.DebuggerConfigurableProvider.EXTENSION_POINT);
  }
}
