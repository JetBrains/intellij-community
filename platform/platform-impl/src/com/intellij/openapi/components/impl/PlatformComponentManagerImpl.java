// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

  @Override
  protected final void registerServices(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    ServiceManagerImpl.registerServices(getServices(pluginDescriptor), pluginDescriptor, this);
  }

  @NotNull
  protected abstract List<ServiceDescriptor> getServices(@NotNull IdeaPluginDescriptor pluginDescriptor);
}
