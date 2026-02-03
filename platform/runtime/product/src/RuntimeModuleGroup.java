// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Describes a group of modules which can be enabled or disabled together.
 *
 * @see ProductModules#getMainModuleGroup()
 * @see ProductModules#getBundledPluginModuleGroups()
 */
public interface RuntimeModuleGroup {
  @NotNull List<@NotNull IncludedRuntimeModule> getIncludedModules();

  /**
   * Returns IDs of modules with {@link RuntimeModuleLoadingRule#OPTIONAL} loading rule, including unresolved ones.
   */
  @NotNull Set<@NotNull RuntimeModuleId> getOptionalModuleIds();

  /**
   * Returns map of modules IDs which are not loaded and reason modules why they are not loaded.
   */
  @NotNull Map<@NotNull RuntimeModuleId, @NotNull List<@NotNull RuntimeModuleId>> getNotLoadedModuleIds();
}
