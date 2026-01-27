// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.internal

import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.jvm.shared.bytecode.BytecodeGenerationResult
import org.jetbrains.kotlin.idea.jvm.shared.bytecode.KotlinBytecodeToolWindow
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
        @OptIn(ExperimentalCompilerApi::class)
        extensionsStorage = CompilerPluginRegistrar.ExtensionStorage()
    }

    val bytecode = runBlockingMaybeCancellable {
        withContext(Dispatchers.Default) {
            readAction {
                KotlinBytecodeToolWindow.getBytecodeForFile(file, configuration, false)
            }
        }
    }

    assert(bytecode is BytecodeGenerationResult.Bytecode) {
        "Exception failed during compilation:\n$bytecode"
    }
}
