// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

abstract class DependenciesElement {
  class ModuleDependency(@JvmField val moduleName: String, @JvmField val namespace: String?): DependenciesElement() {
    override fun toString(): String {
      val namespaceString = if (namespace != null) ", namespace=$namespace" else ""
      return "ModuleDependency(moduleName=$moduleName$namespaceString)"
    }
  }
  class PluginDependency(@JvmField val pluginId: String): DependenciesElement() {
    override fun toString(): String {
      return "PluginDependency(pluginId=$pluginId)"
    }
  }
}