/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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
