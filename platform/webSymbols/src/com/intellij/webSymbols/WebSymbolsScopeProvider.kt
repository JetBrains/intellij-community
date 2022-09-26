// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.impl.WebSymbolsCompoundScope

interface WebSymbolsScopeProvider {

  fun get(context: PsiElement, framework: FrameworkId?): WebSymbolsScope?

  companion object {
    private val EP_NAME = ExtensionPointName.create<WebSymbolsScopeProvider>("com.intellij.webSymbols.scopeProvider")

    @JvmStatic
    fun getScope(context: PsiElement?,
                 framework: FrameworkId?): WebSymbolsScope =
      if (context == null) {
        WebSymbolsCompoundScope(emptyList())
      }
      else {
        val internalMode = ApplicationManager.getApplication().isInternal
        WebSymbolsCompoundScope(EP_NAME.extensionList.mapNotNull { provider ->
          provider.get(context, framework)
            ?.also { scope ->
              if (internalMode && Math.random() < 0.2) {
                val newScope = provider.get(context, framework)
                if (newScope != scope) {
                  logger<WebSymbolsScopeProvider>().error(
                    "Provider $provider should provide scope, which is the same (by equals()), when called with the same arguments: $scope != $newScope")
                }
                if (newScope.hashCode() != scope.hashCode()) {
                  logger<WebSymbolsScopeProvider>().error(
                    "Provider $provider should provide scope, which has the same hashCode(), when called with the same arguments: $scope != $newScope")
                }
              }
            }
        })
      }

  }
}