// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractKotlinLambdasHintsProvider : AbstractKotlinInlayHintsProviderTest() {

    override fun inlayHintsProvider(): InlayHintsProvider =
        org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinLambdasHintsProvider()

    override fun assertThatActualHintsMatch(file: File) {
        with(inlayHintsProvider()) {
            val fileContents = FileUtil.loadFile(file, true)
            val options = buildMap<String, Boolean> {
                put(SHOW_RETURN_EXPRESSIONS.name, false)
                put(SHOW_IMPLICIT_RECEIVERS_AND_PARAMS.name, false)

                when (InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// MODE: ")) {
                    "return" -> put(SHOW_RETURN_EXPRESSIONS.name, true)
                    "receivers_params" -> put(SHOW_IMPLICIT_RECEIVERS_AND_PARAMS.name, true)
                    "return-&-receivers_params" -> {
                        put(SHOW_IMPLICIT_RECEIVERS_AND_PARAMS.name, true)
                        put(SHOW_RETURN_EXPRESSIONS.name, true)
                    }
                }
            }

            try {
                doTestProvider("KotlinLambdasHintsProvider.kt", fileContents, this, options)
            } catch (e: ComparisonFailure) {
                throw FileComparisonFailedError(
                    e.message,
                    e.expected, e.actual, file.absolutePath, null
                )
            }
        }
    }

}