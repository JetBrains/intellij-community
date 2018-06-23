// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl

import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.containers.isNullOrEmpty

private val LOG = Logger.getInstance("#com.intellij.openapi.components.impl.BasePathMacroManager")

internal class TrackingPathMacroSubstitutorImpl(private val macroManager: BasePathMacroManager) : PathMacroSubstitutor by macroManager, TrackingPathMacroSubstitutor {
  private val lock = Object()

  private val macroToComponentNames = MultiMap.createSet<String, String>()
  private val componentNameToMacros = MultiMap.createSet<String, String>()

  override fun reset() {
    synchronized(lock) {
      macroToComponentNames.clear()
      componentNameToMacros.clear()
    }
  }

  override fun hashCode() = macroManager.expandMacroMap.hashCode()

  override fun invalidateUnknownMacros(macros: Set<String>) {
    synchronized(lock) {
      for (macro in macros) {
        val componentNames = macroToComponentNames.remove(macro)
        if (componentNames.isNullOrEmpty()) {
          for (component in componentNames!!) {
            componentNameToMacros.remove(component)
          }
        }
      }
    }
  }

  override fun getComponents(macros: Collection<String>): Set<String> {
    synchronized(lock) {
      val result = SmartHashSet<String>()
      for (macro in macros) {
        result.addAll(macroToComponentNames.get(macro))
      }
      return result
    }
  }

  override fun getUnknownMacros(componentName: String?): Set<String> {
    return synchronized(lock) {
      @Suppress("UNCHECKED_CAST")
      if (componentName == null) macroToComponentNames.keySet() else componentNameToMacros.get(componentName) as Set<String>
    }
  }

  override fun addUnknownMacros(componentName: String, unknownMacros: Collection<String>) {
    if (unknownMacros.isEmpty()) {
      return
    }

    LOG.debug { "Registering unknown macros ${unknownMacros.joinToString(", ")} in component $componentName" }

    synchronized(lock) {
      for (unknownMacro in unknownMacros) {
        macroToComponentNames.putValue(unknownMacro, componentName)
      }

      componentNameToMacros.putValues(componentName, unknownMacros)
    }
  }
}

