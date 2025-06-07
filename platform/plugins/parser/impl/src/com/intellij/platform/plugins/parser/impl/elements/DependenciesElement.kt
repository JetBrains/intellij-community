// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

abstract class DependenciesElement {
  class ModuleDependency(@JvmField val moduleName: String): DependenciesElement() {
    override fun toString(): String {
      return "ModuleDependency(moduleName=$moduleName)"
    }
  }
  class PluginDependency(@JvmField val pluginId: String): DependenciesElement() {
    override fun toString(): String {
      return "PluginDependency(pluginId=$pluginId)"
    }
  }
}