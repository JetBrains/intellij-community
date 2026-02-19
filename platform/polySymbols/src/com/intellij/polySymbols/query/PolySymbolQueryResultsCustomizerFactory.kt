// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.query.impl.PolySymbolCompoundQueryResultsCustomizer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

interface PolySymbolQueryResultsCustomizerFactory {

  fun create(location: PsiElement, context: PolyContext): PolySymbolQueryResultsCustomizer?

  @Suppress("TestOnlyProblems")
  companion object {
    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolQueryResultsCustomizerFactory> =
      ExtensionPointName.create<PolySymbolQueryResultsCustomizerFactory>("com.intellij.polySymbols.queryResultsCustomizerFactory")

    @JvmStatic
    fun getQueryResultsCustomizer(
      location: PsiElement?,
      context: PolyContext,
    ): PolySymbolQueryResultsCustomizer =
      if (location == null) {
        PolySymbolCompoundQueryResultsCustomizer(emptyList())
      }
      else {
        val internalMode = ApplicationManager.getApplication().isInternal
        PolySymbolCompoundQueryResultsCustomizer(EP_NAME.extensionList.mapNotNull { factory ->
          factory.create(location, context)
            ?.also { scope ->
              if (internalMode && Math.random() < 0.2) {
                val newScope = factory.create(location, context)
                if (newScope != scope) {
                  logger<PolySymbolQueryResultsCustomizerFactory>().error(
                    "Factory $factory should provide customizer, which is the same (by equals()), when called with the same arguments: $scope != $newScope")
                }
                if (newScope.hashCode() != scope.hashCode()) {
                  logger<PolySymbolQueryResultsCustomizerFactory>().error(
                    "Factory $factory should provide customizer, which has the same hashCode(), when called with the same arguments: $scope != $newScope")
                }
              }
            }
        })
      }

  }
}