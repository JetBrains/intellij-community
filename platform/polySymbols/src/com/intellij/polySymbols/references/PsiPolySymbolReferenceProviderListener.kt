// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.util.messages.Topic

interface PsiPolySymbolReferenceProviderListener {

  fun beforeProvideReferences(referenceHost: PsiExternalReferenceHost, targetSymbol: Symbol?)

  fun afterProvideReferences(referenceHost: PsiExternalReferenceHost, targetSymbol: Symbol?)

  companion object {
    @JvmField
    val TOPIC: Topic<PsiPolySymbolReferenceProviderListener> = Topic(PsiPolySymbolReferenceProviderListener::class.java)
  }

}