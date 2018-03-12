// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

public class BaseUICollector {
  protected boolean isNotBundledPluginClass(@NotNull Class clazz) {
    ClassLoader loader = clazz.getClassLoader();
    if (loader instanceof PluginClassLoader) {
      PluginId id = ((PluginClassLoader)loader).getPluginId();
      if (id != null) {
        IdeaPluginDescriptor plugin = PluginManager.getPlugin(id);
        if (plugin != null && !plugin.isBundled()) {
          return true;
        }
      }
    }
    return false;
  }
}
