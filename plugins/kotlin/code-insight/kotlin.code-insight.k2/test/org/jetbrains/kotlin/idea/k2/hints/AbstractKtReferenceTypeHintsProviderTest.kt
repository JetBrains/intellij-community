// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.AbstractKotlinReferenceTypeHintsProviderTest
import org.jetbrains.kotlin.idea.codeInsight.hints.KtReferencesTypeHintsProvider

abstract class AbstractKtReferenceTypeHintsProviderTest: AbstractKotlinReferenceTypeHintsProviderTest() {

    override fun isK2Plugin(): Boolean  = true

    override fun inlayHintsProvider(): InlayHintsProvider =
        KtReferencesTypeHintsProvider()
}