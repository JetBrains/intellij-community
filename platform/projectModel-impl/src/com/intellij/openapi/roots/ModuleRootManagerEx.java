// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.impl.RootConfigurationAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public abstract class ModuleRootManagerEx extends ModuleRootManager {

  @NotNull
  public abstract ModifiableRootModel getModifiableModel(@NotNull RootConfigurationAccessor accessor);

  @TestOnly
  public abstract long getModificationCountForTests();

  public abstract void dropCaches();

  public static ModuleRootManagerEx getInstanceEx(@NotNull Module module) {
    return (ModuleRootManagerEx) module.getComponent(ModuleRootManager.class);
  }
}
