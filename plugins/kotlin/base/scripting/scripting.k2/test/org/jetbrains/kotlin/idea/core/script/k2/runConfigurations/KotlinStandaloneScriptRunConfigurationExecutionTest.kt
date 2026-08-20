// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.runConfigurations

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.runConfigurations.jvm.script.KotlinStandaloneScriptRunConfiguration
import org.jetbrains.kotlin.idea.runConfigurations.jvm.script.kotlinStandaloneScriptRunConfigurationType
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File
import java.util.concurrent.TimeUnit

class KotlinStandaloneScriptRunConfigurationExecutionTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    fun testArgsFileMode() {
        assertScriptOutput(ShortenCommandLine.ARGS_FILE)
    }

    fun testManifestMode() {
        assertScriptOutput(ShortenCommandLine.MANIFEST)
    }

    fun testNoneMode() {
        assertScriptOutput(ShortenCommandLine.NONE)
    }

    fun testUnsetMode() {
        assertScriptOutput(shortenCommandLine = null)
    }

    private fun assertScriptOutput(shortenCommandLine: ShortenCommandLine?) {
        val commandLine = commandLine(shortenCommandLine)
        val process = commandLine.createProcess()
        try {
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            assertTrue("Script did not finish in time", process.waitFor(5, TimeUnit.MINUTES))
            assertEquals("Exit code, stdout=$stdout, stderr=$stderr", 0, process.exitValue())
            assertTrue("Expected 'sum=5' in stdout, was '$stdout', stderr='$stderr'", stdout.contains("sum=5"))
        } finally {
            process.destroyForcibly()
        }
    }

    private fun commandLine(shortenCommandLine: ShortenCommandLine?) = run {
        val scriptFile = File(FileUtil.createTempDirectory(getTestName(true), null, true), "script.kts")
        scriptFile.writeText("val sum = 2 + 3\nprintln(\"sum=\$sum\")\n")
        checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(scriptFile)) {
            "Failed to find the script file in VFS: $scriptFile"
        }

        val configuration = KotlinStandaloneScriptRunConfiguration(
            project, kotlinStandaloneScriptRunConfigurationType(), "script.kts"
        )
        configuration.filePath = scriptFile.path
        configuration.shortenCommandLine = shortenCommandLine

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = ExecutionEnvironmentBuilder.create(project, executor, configuration).build()
        val state = configuration.getState(executor, environment) as JavaCommandLineState
        state.javaParameters.toCommandLine()
    }
}
