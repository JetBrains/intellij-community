// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.platform.wasm.isWasm
import org.jetbrains.kotlin.psi.*

internal class WasmMultiplatformSupportAvailability : KotlinSupportAvailability {

    @Nls
    override fun name(): String = KotlinCodeInsightBundle.message("kmp.wasm.support.availability.name")

    override fun isSupported(ktElement: KtElement): Boolean {
        return when {
            isUnitTestMode() -> {
                // we want to test WASM in our unit tests
                true
            }

            Registry.`is`("kotlin.k2.kmp.wasm.enabled", true) -> {
                true
            }

            ktElement.containingKtFile.platform.isWasm() -> {
                false
            }

            else -> true
        }
    }
}