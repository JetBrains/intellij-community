// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractKotlinReferenceTypeHintsProviderTest : AbstractKotlinInlayHintsProviderTest() {

    override fun inlayHintsProvider(): InlayHintsProvider =
        org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinReferencesTypeHintsProvider()

    override fun assertThatActualHintsMatch(file: File) {
        with(inlayHintsProvider()) {
            val fileContents = FileUtil.loadFile(file, true)
            val options = buildMap<String, Boolean> {
                put(SHOW_PROPERTY_TYPES.name, false)
                put(SHOW_LOCAL_VARIABLE_TYPES.name, false)
                put(SHOW_FUNCTION_RETURN_TYPES.name, false)
                put(SHOW_FUNCTION_PARAMETER_TYPES.name, false)
                when (InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// MODE: ")) {
                    "function_return" -> {
                        put(SHOW_PROPERTY_TYPES.name, true)
                        put(SHOW_FUNCTION_RETURN_TYPES.name, true)
                        put(SHOW_LOCAL_VARIABLE_TYPES.name, true)
                    }

                    "local_variable" -> put(SHOW_LOCAL_VARIABLE_TYPES.name, true)
                    "parameter" -> put(SHOW_FUNCTION_PARAMETER_TYPES.name, true)
                    "property" -> {
                        put(SHOW_PROPERTY_TYPES.name, true)
                        put(SHOW_FUNCTION_RETURN_TYPES.name, true)
                    }

                    "all" -> {
                        put(SHOW_PROPERTY_TYPES.name, true)
                        put(SHOW_LOCAL_VARIABLE_TYPES.name, true)
                        put(SHOW_FUNCTION_RETURN_TYPES.name, true)
                        put(SHOW_FUNCTION_PARAMETER_TYPES.name, true)
                    }

                    else -> {}
                }
            }

            try {
                doTestProvider("KotlinReferencesTypeHintsProvider.kt", fileContents, this, options)
            } catch (e: ComparisonFailure) {
                throw FileComparisonFailedError(
                    e.message,
                    e.expected, e.actual, file.absolutePath, null
                )
            }
        }
    }
}