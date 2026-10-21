// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

interface PolySymbolThreadLocalCacheKeyProvider {

  fun getCacheKey(project: Project): Any?

  @Suppress("TestOnlyProblems")
  companion object {
    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolThreadLocalCacheKeyProvider> =
      ExtensionPointName.create<PolySymbolThreadLocalCacheKeyProvider>("com.intellij.polySymbols.threadLocalCacheKeyProvider")

    @ApiStatus.Internal
    fun getCacheKeys(initialCacheKey: Any?, project: Project): List<Any?> {
      val extensions = EP_NAME.extensionList
      val result = ArrayList<Any?>(extensions.size + 1)
      result.add(initialCacheKey)
      for (ext in extensions) {
        result.add(ext.getCacheKey(project))
      }
      return result
    }

  }

}