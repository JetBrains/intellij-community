/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.components.ex;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.notification.Notification;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author max
 */
public interface ComponentManagerEx extends ComponentManager {
  /**
   * @deprecated Use {@link #registerComponent(com.intellij.openapi.components.ComponentConfig)} istead
   */
  void registerComponent(Class interfaceClass, Class implementationClass);

  /**
   * @deprecated Use {@link #registerComponent(com.intellij.openapi.components.ComponentConfig)} istead
   */
  void registerComponent(Class interfaceClass, Class implementationClass, Map options);

  void registerComponent(ComponentConfig config);
  void registerComponent(ComponentConfig config, IdeaPluginDescriptor pluginDescriptor);

  IComponentStore getComponentStore();
}
