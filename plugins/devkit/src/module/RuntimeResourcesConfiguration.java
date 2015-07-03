/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class RuntimeResourcesConfiguration extends ModuleExtension<RuntimeResourcesConfiguration> {
  public static RuntimeResourcesConfiguration getInstance(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(RuntimeResourcesConfiguration.class);
  }

  @NotNull
  public abstract Collection<RuntimeResourceRoot> getRoots();

  @Nullable
  public abstract RuntimeResourceRoot getRoot(@NotNull String name);

  public abstract void setRoots(@NotNull List<RuntimeResourceRoot> roots);
}
