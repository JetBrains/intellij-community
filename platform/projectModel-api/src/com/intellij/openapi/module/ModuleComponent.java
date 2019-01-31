// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.project.Project;

/**
 * Base interface for module-level components. The constructor of the classes
 * implementing this interface can accept as parameters the module instance and
 * any application-, project- or module-level components this component depends on.
 *
 * <p>
 * <strong>Note that if you register a class as a module component it will be loaded, its instance will be created and
 * {@link #initComponent()}, {@link #moduleAdded()} methods will be called for each module even if user doesn't use
 * any feature of your plugin. So consider using specific extensions instead to ensure that the plugin will not impact IDE performance
 * until user calls its actions explicitly.</strong>
 *
 * Consider to use {@link com.intellij.ProjectTopics#MODULES}
 */
public interface ModuleComponent extends BaseComponent {
  /**
   * Invoked when the project corresponding to this component instance is opened.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component instance (for example for default project).
   *
   * @deprecated Please use {@link com.intellij.openapi.project.ProjectManager#TOPIC} ({@link com.intellij.openapi.project.ProjectManagerListener#projectOpened(Project)} (Project, Module)})
   */
  @Deprecated
  default void projectOpened() {
  }

  /**
   * Invoked when the project corresponding to this component instance is closed.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component instance (for example for default project).
   *
   * @deprecated Please use {@link com.intellij.openapi.project.ProjectManager#TOPIC} ({@link com.intellij.openapi.project.ProjectManagerListener#projectClosed(Project)} (Project, Module)})
   */
  @Deprecated
  default void projectClosed() {
  }

  /**
   * Invoked when the module corresponding to this component instance has been completely
   * loaded and added to the project.
   *
   * Consider to use {@link com.intellij.ProjectTopics#MODULES} ({@link com.intellij.openapi.project.ModuleListener#moduleAdded(Project, Module)})
   */
  default void moduleAdded() {
  }
}
