// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.impl.RootConfigurationAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public abstract class ModuleRootManagerEx extends ModuleRootManager {
  public static final ProjectExtensionPointName<ModuleExtension> MODULE_EXTENSION_NAME = new ProjectExtensionPointName<>("com.intellij.moduleExtension");

  @NotNull
  public abstract ModifiableRootModel getModifiableModel(@NotNull RootConfigurationAccessor accessor);

  /**
   * This method was introduced only for new workspace model and should not be used in other places. Instance of {@link ModifiableRootModel}
   * returned by this method cannot be disposed (unless its module is removed) and must be committed together with its {@link com.intellij.openapi.module.ModifiableModuleModel}
   * via {@link com.intellij.openapi.roots.impl.ModifiableModelCommitterService#multiCommit}.
   */
  @ApiStatus.Internal
  @NotNull
  public ModifiableRootModel getModifiableModelForMultiCommit(@NotNull RootConfigurationAccessor accessor) {
    return getModifiableModel(accessor);
  }

  @TestOnly
  public abstract long getModificationCountForTests();

  public abstract void dropCaches();

  public static ModuleRootManagerEx getInstanceEx(@NotNull Module module) {
    return (ModuleRootManagerEx) module.getComponent(ModuleRootManager.class);
  }
}
