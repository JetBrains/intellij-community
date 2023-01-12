// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.query.impl.WebSymbolsCompoundQueryResultsCustomizer

interface WebSymbolsQueryResultsCustomizerFactory {

  fun create(location: PsiElement, context: WebSymbolsContext): WebSymbolsQueryResultsCustomizer?

  companion object {
    private val EP_NAME = ExtensionPointName.create<WebSymbolsQueryResultsCustomizerFactory>("com.intellij.webSymbols.queryResultsCustomizerFactory")

    @JvmStatic
    fun getScope(location: PsiElement?,
                 context: WebSymbolsContext): WebSymbolsQueryResultsCustomizer =
      if (location == null) {
        WebSymbolsCompoundQueryResultsCustomizer(emptyList())
      }
      else {
        val internalMode = ApplicationManager.getApplication().isInternal
        WebSymbolsCompoundQueryResultsCustomizer(EP_NAME.extensionList.mapNotNull { factory ->
          factory.create(location, context)
            ?.also { scope ->
              if (internalMode && Math.random() < 0.2) {
                val newScope = factory.create(location, context)
                if (newScope != scope) {
                  logger<WebSymbolsQueryResultsCustomizerFactory>().error(
                    "Factory $factory should provide customizer, which is the same (by equals()), when called with the same arguments: $scope != $newScope")
                }
                if (newScope.hashCode() != scope.hashCode()) {
                  logger<WebSymbolsQueryResultsCustomizerFactory>().error(
                    "Factory $factory should provide customizer, which has the same hashCode(), when called with the same arguments: $scope != $newScope")
                }
              }
            }
        })
      }

  }
}