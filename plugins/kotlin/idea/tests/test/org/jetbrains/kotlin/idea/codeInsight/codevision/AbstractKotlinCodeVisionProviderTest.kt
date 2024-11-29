// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.io.File

abstract class AbstractKotlinCodeVisionProviderTest :
    CodeVisionTestCase(),
    ExpectedPluginModeProvider { // Abstract- prefix is just a convention for GenerateTests

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    fun doTest(testPath: String) { // named according to the convention imposed by GenerateTests
        assertThatActualHintsMatch(testPath)
    }

    private fun assertThatActualHintsMatch(fileName: String) {
        val file = File(fileName)
        val fileContents = FileUtil.loadFile(file, true)

        val mode = when (InTextDirectivesUtils.findStringWithPrefixes(fileContents, "// MODE: ")) {
            "inheritors" -> arrayOf("inheritors")
            "usages" -> arrayOf("references")
            "usages-&-inheritors" -> arrayOf("references", "inheritors")
            else -> emptyArray()
        }

        testProviders(fileContents, file.name, *mode)
    }
}
