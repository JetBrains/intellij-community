// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.internal

import com.intellij.openapi.application.readAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.idea.actions.bytecode.BytecodeGenerationResult
import org.jetbrains.kotlin.idea.actions.bytecode.KotlinBytecodeToolWindow
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractBytecodeToolWindowMultiplatformTest : AbstractMultiModuleTest() {

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("internal/toolWindowMultiplatform")

    fun doTestCommon(testPath: String) = doTest(testPath, "Common")
    fun doTestJvm(testPath: String) = doTest(testPath, "Jvm")

    fun doTest(testPath: String, platform: String) {
        setupMppProjectFromDirStructure(File(testPath))
        val file = project.allKotlinFiles().single { it.name.contains(platform) }
        configureCompilerAndCheckBytecode(file)
    }
}

private fun configureCompilerAndCheckBytecode(file: KtFile) {
    val configuration = CompilerConfiguration().apply {
        languageVersionSettings = file.languageVersionSettings
    }

    val bytecode = runBlocking(Dispatchers.Default) {
        readAction {
            KotlinBytecodeToolWindow.getBytecodeForFile(file, configuration, false)
        }
    }

    assert(bytecode is BytecodeGenerationResult.Bytecode) {
        "Exception failed during compilation:\n$bytecode"
    }
}
