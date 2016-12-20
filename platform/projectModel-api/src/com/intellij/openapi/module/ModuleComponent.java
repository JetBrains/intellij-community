/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.openapi.components.BaseComponent;

/**
 * Base interface for module-level components. The constructor of the classes
 * implementing this interface can accept as parameters the module instance and
 * any application-, project- or module-level components this component depends on.
 *
 * <p>
 * <strong>Note that if you register a class as a module component it will be loaded, its instance will be created and
 * {@link #initComponent()}, {@link #projectOpened()}, {@link #moduleAdded()} methods will be called for each module even if user doesn't use
 * any feature of your plugin. So consider using specific extensions instead to ensure that the plugin will not impact IDE performance
 * until user calls its actions explicitly.</strong>
 */
public interface ModuleComponent extends BaseComponent {
  /**
   * Invoked when the project corresponding to this component instance is opened.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component instance (for example for default project).
   */
  void projectOpened();

  /**
   * Invoked when the project corresponding to this component instance is closed.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component instance (for example for default project).
   */
  void projectClosed();

  /**
   * Invoked when the module corresponding to this component instance has been completely
   * loaded and added to the project.
   */
  void moduleAdded();
}
