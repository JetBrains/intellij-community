// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import java.io.File

abstract class AbstractKotlinRangesHintsProviderTest :
    DeclarativeInlayHintsProviderTestCase() { // Abstract-prefix is just a convention for GenerateTests

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    fun doTest(testPath: String) { // named according to the convention imposed by GenerateTests
        val fileContents = FileUtil.loadFile(File(testPath), true)
        withCustomCompilerOptions(fileContents, project, module) {
            assertThatActualHintsMatch(testPath)
        }
    }

    private fun assertThatActualHintsMatch(fileContents: String) {
        with(org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinValuesHintsProvider()) {
            doTestProvider("KotlinValuesHintsProvider.kt", fileContents, this)
        }
    }
}