// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.codeInsight.hints.AbstractKotlinInlayHintsProviderTest
import org.jetbrains.kotlin.idea.codeInsight.hints.KtParameterHintsProvider
import java.io.File

abstract class AbstractKtParameterHintsProviderTest: AbstractKotlinInlayHintsProviderTest() {
    override fun isK2Plugin(): Boolean = true

    override fun inlayHintsProvider(): InlayHintsProvider =
        KtParameterHintsProvider()

    override fun assertThatActualHintsMatch(file: File) {
        val fileContents = FileUtil.loadFile(file, true)
        with(inlayHintsProvider()) {
            try {
                doTestProvider("KtParameterHintsProvider.kt", fileContents, this)
            } catch (e: ComparisonFailure) {
                throw FileComparisonFailedError(
                    e.message,
                    e.expected, e.actual, file.absolutePath, null
                )
            }
        }
    }
}