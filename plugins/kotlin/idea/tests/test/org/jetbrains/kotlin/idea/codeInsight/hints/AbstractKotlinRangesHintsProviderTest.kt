// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import java.io.File

abstract class AbstractKotlinRangesHintsProviderTest : AbstractKotlinInlayHintsProviderTest() {

    override fun inlayHintsProvider(): InlayHintsProvider =
        org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinValuesHintsProvider()

    override fun assertThatActualHintsMatch(file: File) {
        val fileContents = FileUtil.loadFile(file, true)
        withCustomCompilerOptions(fileContents, project, module) {
            with(inlayHintsProvider()) {
                try {
                    doTestProvider("KotlinValuesHintsProvider.kt", fileContents, this)
                } catch (e: ComparisonFailure) {
                    throw FileComparisonFailedError(
                        e.message,
                        e.expected, e.actual, file.absolutePath, null
                    )
                }
            }
        }
    }
}