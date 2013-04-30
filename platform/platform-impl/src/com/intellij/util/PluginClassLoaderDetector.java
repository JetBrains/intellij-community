/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;

public class PluginClassLoaderDetector implements Function<String, ClassLoader> {
  private static final PluginClassLoaderDetector INSTANCE = new PluginClassLoaderDetector();

  @Override
  public ClassLoader fun(String className) {
    PluginId pluginId = PluginManager.getPluginByClassName(className);
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    if (plugin == null) return null;
    return plugin.getPluginClassLoader();
  }

  public static void install() {
    ReflectionUtil.PLUGIN_CLASS_LOADER_DETECTOR = INSTANCE;
  }
}
