// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product

import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor

/**
 * Returned from [ProductModules.getBundledPluginModuleGroups].
 */
interface PluginModuleGroup : RuntimeModuleGroup {
  /**
   * Returns the main module of the plugin (which contains META-INF/plugin.xml file).
   */
  val mainModule: RuntimeModuleDescriptor
}