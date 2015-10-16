/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PlatformComponentManagerImpl extends ComponentManagerImpl {
  private boolean myHandlingInitComponentError;

  protected PlatformComponentManagerImpl(@Nullable ComponentManager parent) {
    super(parent);
  }

  protected PlatformComponentManagerImpl(@Nullable ComponentManager parent, @NotNull String name) {
    super(parent, name);
  }

  @Override
  protected void handleInitComponentError(Throwable t, String componentClassName, PluginId pluginId) {
    if (!myHandlingInitComponentError) {
      myHandlingInitComponentError = true;
      try {
        PluginManager.handleComponentError(t, componentClassName, pluginId);
      }
      finally {
        myHandlingInitComponentError = false;
      }
    }
  }

  @Override
  public void initializeComponent(@NotNull Object component, boolean service) {
    if (!service || !(component instanceof PathMacroManager || component instanceof IComponentStore)) {
      ServiceKt.getStateStore(this).initComponent(component, service);
    }
  }
}
