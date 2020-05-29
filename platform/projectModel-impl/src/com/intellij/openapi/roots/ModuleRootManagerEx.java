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
   * This method was introduced only for new workspace model and should not be used anywhere else except of
   * {@link com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl#doGetModifiableRootModel}
   * In workspace model, we should de distinguish {@link ModifiableRootModel} created from IdeModifiableModelsProviderImpl
   * or from elsewhere. The main problem in the new model that if we will not do this we will get a lot of records
   * with the different `entitySources`. From our perspective, it will be removed when we will switch externalSystem
   * to work with the store directly.
   */
  @ApiStatus.Internal
  @NotNull
  public ModifiableRootModel getModifiableModelForExternalSystem(@NotNull RootConfigurationAccessor accessor) {
    return getModifiableModel(accessor);
  }

  @TestOnly
  public abstract long getModificationCountForTests();

  public abstract void dropCaches();

  public static ModuleRootManagerEx getInstanceEx(@NotNull Module module) {
    return (ModuleRootManagerEx) module.getComponent(ModuleRootManager.class);
  }
}
