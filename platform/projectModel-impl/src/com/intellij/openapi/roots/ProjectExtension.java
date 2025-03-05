// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.projectRoots.Sdk;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a way to store additional configuration for a project. Plugins must not use this class, but use project-level
 * <a href="https://plugins.jetbrains.com/docs/intellij/plugin-services.html">services</a> implementing {@link com.intellij.openapi.components.PersistentStateComponent}
 * instead.
 *
 * <p>This class is used for some data which is historically stored as part of {@code ProjectRootManager} configuration in .idea/misc.xml file.</p>
 */
@ApiStatus.Internal
public abstract class ProjectExtension {
  public void projectSdkChanged(@Nullable Sdk sdk) {
  }

  /**
   * Returns true if the state was changed after read
   */
  public abstract boolean readExternalElement(@NotNull Element element);

  public abstract void writeExternal(@NotNull Element element);
}