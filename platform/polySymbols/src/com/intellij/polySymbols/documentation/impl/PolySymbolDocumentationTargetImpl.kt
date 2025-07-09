// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation.impl

import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.documentation.PolySymbolDocumentationProvider
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer

internal class PolySymbolDocumentationTargetImpl<T : PolySymbol>(
  override val symbol: T,
  override val location: PsiElement?,
  private val provider: PolySymbolDocumentationProvider<T>,
) : PolySymbolDocumentationTarget {

  override fun computePresentation(): TargetPresentation =
    provider.computePresentation(symbol)
    ?: TargetPresentation.builder(symbol.name)
      .icon(symbol.icon)
      .presentation()

  override val documentation: PolySymbolDocumentation by lazy(LazyThreadSafetyMode.PUBLICATION) {
    this.provider.createDocumentation(symbol, location)
  }

  override fun computeDocumentationHint(): @NlsContexts.HintText String? =
    documentation.let { it.definitionDetails ?: it.definition }

  override val navigatable: Navigatable?
    get() = (location?.project ?: symbol.psiContext?.project)
      ?.let { symbol.getNavigationTargets(it) }
      ?.firstOrNull()
      ?.let {
        object : Navigatable {
          override fun navigationRequest(): NavigationRequest? =
            it.navigationRequest()
        }
      }

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val pointer = symbol.createPointer()
    val locationPtr = location?.createSmartPointer()
    val provider = this.provider
    return Pointer<DocumentationTarget> {
      pointer.dereference()?.let {
        @Suppress("UNCHECKED_CAST")
        PolySymbolDocumentationTargetImpl(it as T, locationPtr?.dereference(), provider)
      }
    }
  }

  override fun computeDocumentation(): DocumentationResult? =
    documentation.takeIf { it.isNotEmpty() }
      ?.build(symbol.origin)

  companion object {
    internal fun check(lambda: Any) {
      if (!ApplicationManager.getApplication().let { it.isUnitTestMode || it.isInternal || it.isEAP }) return
      if (lambda::class.java.declaredFields.any { it.name.startsWith("arg$") || it.name.startsWith("this$") }) {
        thisLogger().error("Do not capture object instance or method parameters in documentation target builder lambda : $lambda")
      }
    }
  }


}