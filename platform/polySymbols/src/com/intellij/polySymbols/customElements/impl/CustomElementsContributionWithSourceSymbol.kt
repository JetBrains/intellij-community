// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiElement
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.json.CustomElementsContributionWithSource

abstract class CustomElementsContributionWithSourceSymbol<T : CustomElementsContributionWithSource> protected constructor(
  name: String,
  contribution: T,
  origin: CustomElementsJsonOrigin,
) : CustomElementsContributionSymbol<T>(name, contribution, origin), PsiSourcedPolySymbol {

  private val cacheHolder = UserDataHolderBase()

  override val source: PsiElement?
    get() = contribution.source?.let { origin.resolveSourceSymbol(it, cacheHolder) }

  override fun createPointer(): Pointer<out CustomElementsContributionWithSourceSymbol<T>> =
    Pointer.hardPointer(this)

}