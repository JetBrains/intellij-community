// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils

abstract class AbstractKotlinLambdasHintsProvider : AbstractKotlinInlayHintsProviderTest() {

    override fun inlayHintsProvider(): InlayHintsProvider =
        org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinLambdasHintsProvider()

    override fun calculateOptions(fileContents: String): Map<String, Boolean> =
        buildMap {
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
}