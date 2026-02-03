// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization

import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import java.io.IOException
import java.io.InputStream

class RawProductModules internal constructor(
  val mainGroupModules: List<RawIncludedRuntimeModule>,
  val bundledPluginMainModules: List<RuntimeModuleId>,
  val includedFrom: List<RawIncludedFromData>,
)

class RawIncludedFromData internal constructor(
  val fromModule: RuntimeModuleId,
  val withoutModules: Set<RuntimeModuleId>,
)

interface ResourceFileResolver {
  /**
   * Reads content of file with [relativePath] from [moduleId] if it's present or `null` if it cannot be resolved.
   */
  @Throws(IOException::class)
  fun readResourceFile(moduleId: RuntimeModuleId, relativePath: String): InputStream?
  
  companion object {
    /**
     * Returns the default variant of [ResourceFileResolver] which load files from module classes roots specified in [moduleRepository]. 
     */
    @JvmStatic
    fun createDefault(moduleRepository: RuntimeModuleRepository): ResourceFileResolver {
      return object : ResourceFileResolver {
        override fun readResourceFile(moduleId: RuntimeModuleId, relativePath: String): InputStream? {
          return moduleRepository.getModule(moduleId).readFile(relativePath)
        }

        override fun toString(): String {
          return "default resolver"
        }
      }
    } 
  }
}