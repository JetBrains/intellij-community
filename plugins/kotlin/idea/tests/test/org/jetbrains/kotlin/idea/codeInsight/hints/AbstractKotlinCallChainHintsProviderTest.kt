// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider

abstract class AbstractKotlinCallChainHintsProviderTest : AbstractKotlinInlayHintsProviderTest() {
    override fun inlayHintsProvider(): InlayHintsProvider =
        org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinCallChainHintsProvider()
}