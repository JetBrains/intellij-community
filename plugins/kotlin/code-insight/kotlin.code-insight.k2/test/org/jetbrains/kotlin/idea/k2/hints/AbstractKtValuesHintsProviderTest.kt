// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.AbstractKotlinValuesHintsProviderTest
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_KOTLIN_TIME
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_RANGES
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.KtValuesHintsProvider

abstract class AbstractKtValuesHintsProviderTest: AbstractKotlinValuesHintsProviderTest() {

    override fun inlayHintsProvider(): InlayHintsProvider =
        KtValuesHintsProvider()

    override fun calculateOptions(fileContents: String): Map<String, Boolean> =
        buildMap {
            put(SHOW_RANGES.name, true)
            put(SHOW_KOTLIN_TIME.name, true)
        }
}