// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.projectRoots.Sdk;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a way to store additional configuration for a project. Plugins aren't supposed to use this class, they show use project-level
 * <a href="https://plugins.jetbrains.com/docs/intellij/plugin-services.html">services</a> which implements {@link com.intellij.openapi.components.PersistentStateComponent}
 * instead.
 *
 * <p>This class is used for some data which is historically stored as part of {@code ProjectRootManager} configuration in .idea/misc.xml file.</p>
 */
@ApiStatus.Internal
public abstract class ProjectExtension {
  public void projectSdkChanged(@Nullable Sdk sdk) {
  }

  public abstract void readExternal(@NotNull Element element);

  public abstract void writeExternal(@NotNull Element element);
}