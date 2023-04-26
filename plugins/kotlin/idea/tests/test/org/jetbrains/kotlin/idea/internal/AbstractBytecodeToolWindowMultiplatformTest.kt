// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.internal

import java.io.File
import java.nio.file.Paths
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractBytecodeToolWindowMultiplatformTest : AbstractMultiModuleTest() {

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("internal/toolWindowMultiplatform")

    fun doTestWithIrCommon(testPath: String) = doTest(testPath, true, "Common")
    fun doTestWithoutIrCommon(testPath: String) = doTest(testPath, false, "Common")
    fun doTestWithIrJvm(testPath: String) = doTest(testPath, true, "Jvm")
    fun doTestWithoutIrJvm(testPath: String) = doTest(testPath, false, "Jvm")

    fun doTest(testPath: String, withIr: Boolean, platform: String) {
        setupMppProjectFromDirStructure(File(testPath))
        val file = project.allKotlinFiles().filter { it.name.contains(platform) }.single()

        //TODO: remove ignoring logic after KTIJ-25152 fixed
        if (withIr) {
            IgnoreTests.runTestIfNotDisabledByFileDirective(Paths.get(file.virtualFilePath), IgnoreTests.DIRECTIVES.IGNORE_FE10) {
                configureCompilerAndCheckBytecode(withIr, file)
            }
        } else {
            configureCompilerAndCheckBytecode(withIr, file)
        }
    }
}

private fun configureCompilerAndCheckBytecode(withIr: Boolean, file: KtFile) {
    val configuration = CompilerConfiguration().apply {
        if (withIr) put(JVMConfigurationKeys.IR, true)
        languageVersionSettings = file.languageVersionSettings
    }

    val bytecodes = KotlinBytecodeToolWindow.getBytecodeForFile(file, configuration)
    assert(bytecodes is BytecodeGenerationResult.Bytecode) {
        "Exception failed during compilation:\n$bytecodes"
    }
}
