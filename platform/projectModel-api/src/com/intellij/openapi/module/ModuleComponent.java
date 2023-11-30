// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * @deprecated Components are deprecated, please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-components.html">SDK Docs</a> for guidelines on migrating to other APIs.
 * <p>
 * If you register a class as a module component it will be loaded, its instance will be created and
 * {@link #initComponent()}, {@link #moduleAdded()} methods will be called for each module even if user doesn't use
 * any feature of your plugin. Also, plugins declaring module components do not support dynamic loading.
 */
@Deprecated(forRemoval = true)
public interface ModuleComponent extends BaseComponent {
  /**
   * Invoked when the module corresponding to this component instance has been completely
   * loaded and added to the project.
   *
   * @deprecated Use {@link com.intellij.openapi.project.ModuleListener#TOPIC} ({@link com.intellij.openapi.project.ModuleListener#modulesAdded(Project, List)})
   */
  @Deprecated(forRemoval = true)
  default void moduleAdded() {
  }
}
