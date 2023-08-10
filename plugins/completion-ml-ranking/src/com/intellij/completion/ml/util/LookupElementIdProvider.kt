// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.util

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LookupElementIdProvider {
  companion object {
    private val EP = ExtensionPointName.create<LookupElementIdProvider>("com.intellij.completion.ml.elementIdProvider")
    private val LOG = logger<LookupElementIdProvider>()

    fun tryGetIdString(lookupElement: LookupElement): String? {
      val providers = EP.extensionList.filter { it.isApplicable(lookupElement) }
      if (providers.size > 1) {
        val classNames = providers.joinToString { it::class.java.simpleName }
        LOG.error("Lookup element should one applicable id providers, but found: $classNames")
      }
      return providers.singleOrNull()?.getIdString(lookupElement)
    }
  }
  fun isApplicable(e: LookupElement): Boolean
  fun getIdString(e: LookupElement): String
}