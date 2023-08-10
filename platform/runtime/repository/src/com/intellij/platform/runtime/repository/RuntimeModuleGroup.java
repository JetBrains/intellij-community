// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Describes a group of modules which can be enabled or disabled together.
 * @see ProductModules#getMainModuleGroup() 
 * @see ProductModules#getBundledPluginModuleGroups() 
 */
public interface RuntimeModuleGroup {
  @NotNull List<@NotNull IncludedRuntimeModule> getIncludedModules();
}
