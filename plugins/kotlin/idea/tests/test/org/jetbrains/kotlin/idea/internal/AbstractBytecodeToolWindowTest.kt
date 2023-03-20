// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.internal

import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractBytecodeToolWindowTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun doTest(testPath: String) {
        val mainDir = File(testPath)
        val mainFileName = mainDir.name + ".kt"
        mainDir.listFiles { _, name -> name != mainFileName }.forEach { myFixture.configureByFile(testPath + "/" + it.name) }

        val mainFileText = File("$testPath/$mainFileName").readText()
        myFixture.configureByText(KotlinFileType.INSTANCE, mainFileText)

        val file = myFixture.file as KtFile

        val configuration = CompilerConfiguration().apply {
            if (InTextDirectivesUtils.getPrefixedBoolean(mainFileText, "// INLINE:") == false) {
                put(CommonConfigurationKeys.DISABLE_INLINE, true)
            }

            languageVersionSettings = file.languageVersionSettings
        }

        val bytecodes = KotlinBytecodeToolWindow.getBytecodeForFile(file, configuration)
        assert(bytecodes is BytecodeGenerationResult.Bytecode) {
            "Exception failed during compilation:\n$bytecodes"
        }
    }
}
