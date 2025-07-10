// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.util.messages.Topic

interface PolySymbolReferenceProviderListener {

  fun beforeProvideReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints)

  fun afterProvideReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints)

  companion object {
    @JvmField
    val TOPIC: Topic<PolySymbolReferenceProviderListener> = Topic(PolySymbolReferenceProviderListener::class.java)
  }

}