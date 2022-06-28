// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.project.Project;

/**
 * @deprecated Components are deprecated. If you register a class as a module component it will be loaded, its instance will be created and
 * {@link #initComponent()}, {@link #moduleAdded()} methods will be called for each module even if user doesn't use
 * any feature of your plugin. Also, plugins declaring module components do not support dynamic loading.
 * <p/>
 * Please see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_components.html">SDK Docs</a> for guidelines on migrating to other APIs.
 */
@Deprecated
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
   * <p>
   * @deprecated Consider to use {@link com.intellij.ProjectTopics#MODULES} ({@link com.intellij.openapi.project.ModuleListener#moduleAdded(Project, Module)})
   */
  @Deprecated
  default void moduleAdded() {
  }
}
