// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractKotlinCodeVisionProviderTest :
    InlayHintsProviderTestCase() { // Abstract- prefix is just a convention for GenerateTests

    companion object {
        const val INHERITORS_KEY = "kotlin.code-vision.inheritors"
        const val USAGES_KEY = "kotlin.code-vision.usages"
    }

    fun doTest(testPath: String) { // named according to the convention imposed by GenerateTests
        assertThatActualHintsMatch(testPath)
    }

    private fun assertThatActualHintsMatch(fileName: String) {
        val fileContents = FileUtil.loadFile(File(fileName), true)

        val usagesLimit = InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// USAGES-LIMIT: ")?.toInt() ?: 100
        val inheritorsLimit = InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// INHERITORS-LIMIT: ")?.toInt() ?: 100

        val provider = KotlinCodeVisionProvider()
        provider.usagesLimit = usagesLimit
        provider.inheritorsLimit = inheritorsLimit

        when (InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// MODE: ")) {
            "inheritors" -> provider.mode(usages = false, inheritors = true)
            "usages" -> provider.mode(usages = true, inheritors = false)
            "usages-&-inheritors" -> provider.mode(usages = true, inheritors = true)
            else -> provider.mode(usages = false, inheritors = false)
        }

        doTestProvider("kotlinCodeVision.kt", fileContents, provider)
    }

    private fun KotlinCodeVisionProvider.mode(usages: Boolean, inheritors: Boolean) {
        showUsages = usages
        showInheritors = inheritors
    }
}
