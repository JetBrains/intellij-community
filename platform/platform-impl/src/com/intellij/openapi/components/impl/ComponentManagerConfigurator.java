package com.intellij.openapi.components.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.components.ComponentConfig;

class ComponentManagerConfigurator {
  private final ComponentManagerImpl myComponentManager;

  public ComponentManagerConfigurator(final ComponentManagerImpl componentManager) {
    myComponentManager = componentManager;
  }

  private void loadConfiguration(final ComponentConfig[] configs, final boolean defaultProject, final IdeaPluginDescriptor descriptor) {
    for (ComponentConfig config : configs) {
      loadSingleConfig(defaultProject, config, descriptor);
    }
  }

  private void loadSingleConfig(final boolean defaultProject, final ComponentConfig config, final IdeaPluginDescriptor descriptor) {
    if (defaultProject && config.skipForDefaultProject) return;
    if (!myComponentManager.isComponentSuitable(config.options)) return;

    myComponentManager.registerComponent(config, descriptor);
  }

  public void loadComponentsConfiguration(final ComponentConfig[] components,
                                          final IdeaPluginDescriptor descriptor,
                                          final boolean defaultProject) {
    if (components == null) return;

    loadConfiguration(components, defaultProject, descriptor);
  }
}
