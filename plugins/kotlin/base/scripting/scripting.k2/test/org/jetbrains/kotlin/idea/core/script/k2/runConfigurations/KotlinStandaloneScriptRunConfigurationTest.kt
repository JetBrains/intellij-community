// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.runConfigurations

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.runConfigurations.jvm.script.KotlinStandaloneScriptRunConfiguration
import org.jetbrains.kotlin.idea.runConfigurations.jvm.script.kotlinStandaloneScriptRunConfigurationType
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

class KotlinStandaloneScriptRunConfigurationTest : KotlinLightCodeInsightFixtureTestCase() {

    private var registeredJre: Sdk? = null

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun tearDown() {
        try {
            registeredJre?.let { jre -> runWriteAction { ProjectJdkTable.getInstance().removeJdk(jre) } }
            registeredJre = null
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testArgsFileMode() {
        val parameters = javaParameters(ShortenCommandLine.ARGS_FILE)
        assertTrue("arg file", parameters.isArgFile)
        assertFalse("classpath jar", parameters.isUseClasspathJar)
        assertFalse("classpath file", parameters.isClasspathFile)
        assertShortensEverything(parameters)
    }

    fun testManifestMode() {
        val parameters = javaParameters(ShortenCommandLine.MANIFEST)
        assertTrue("classpath jar", parameters.isUseClasspathJar)
        assertFalse("arg file", parameters.isArgFile)
        assertOnlyClasspathIsShortened(parameters)
    }

    fun testClasspathFileMode() {
        val parameters = javaParameters(ShortenCommandLine.CLASSPATH_FILE)
        assertTrue("classpath file", parameters.isClasspathFile)
        assertFalse("arg file", parameters.isArgFile)
        assertOnlyClasspathIsShortened(parameters)
    }

    fun testNoneMode() {
        val parameters = javaParameters(ShortenCommandLine.NONE)
        assertFalse("classpath must not be shortened", parameters.isDynamicClasspath)
        assertFalse("arg file", parameters.isArgFile)
        assertFalse("classpath jar", parameters.isUseClasspathJar)
        assertFalse("classpath file", parameters.isClasspathFile)
    }

    fun testUnsetModeStillShortens() {
        val parameters = javaParameters(shortenCommandLine = null)
        assertTrue("classpath must be shortened by default", parameters.isDynamicClasspath)
        assertEquals("program parameters", parameters.isArgFile, parameters.isDynamicParameters)
        assertEquals("vm options", parameters.isArgFile, parameters.isDynamicVMOptions)
    }

    fun testModuleJdkIsUsedByDefault() {
        val parameters = javaParameters(configuration())
        assertEquals(moduleSdkHome(), parameters.jdk?.homePath)
    }

    fun testAlternativeJreOverridesModuleJdk() {
        val alternativeJre = registerAlternativeJre()
        val parameters = javaParameters(configuration().apply {
            isAlternativeJrePathEnabled = true
            alternativeJrePath = alternativeJre.name
        })
        assertEquals(alternativeJre.homePath, parameters.jdk?.homePath)
    }

    fun testAlternativeJreIgnoredWhenDisabled() {
        val parameters = javaParameters(configuration().apply {
            isAlternativeJrePathEnabled = false
            alternativeJrePath = "/definitely/not/a/real/jdk/home"
        })
        assertEquals(moduleSdkHome(), parameters.jdk?.homePath)
    }

    fun testScriptOutsideProjectHasNoModule() {
        val configuration = configuration()
        assertEmpty(configuration.modules)
        assertFalse(
            "a script outside project sources must not pull in module dependencies",
            compilerClasspath(configuration).any { it.contains("kotlin-stdlib") },
        )
    }

    fun testScriptInsideModuleUsesItsClasspath() {
        val sourceRoot = FileUtil.createTempDirectory(getTestName(true), "src", true)
        val virtualSourceRoot = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceRoot))
        PsiTestUtil.addSourceContentToRoots(module, virtualSourceRoot)
        try {
            val configuration = configuration(File(sourceRoot, "script.kts"))
            assertEquals(listOf(module), configuration.modules.toList())
            assertTrue(
                "a script inside a module must pull in that module's dependencies",
                compilerClasspath(configuration).any { it.contains("kotlin-stdlib") },
            )
        } finally {
            PsiTestUtil.removeContentEntry(module, virtualSourceRoot)
        }
    }

    private fun registerAlternativeJre(): Sdk {
        val jdk = IdeaTestUtil.getMockJdk17()
        runWriteAction { ProjectJdkTable.getInstance().addJdk(jdk) }
        registeredJre = jdk
        return jdk
    }

    private fun moduleSdkHome(): String? =
        checkNotNull(ModuleRootManager.getInstance(module).sdk) { "fixture must expose a module SDK" }.homePath

    private fun compilerClasspath(configuration: KotlinStandaloneScriptRunConfiguration): List<String> {
        val programParameters = javaParameters(configuration).programParametersList.parameters
        val classpathIndex = programParameters.indexOf("-cp")
        if (classpathIndex < 0) return emptyList()
        return programParameters[classpathIndex + 1].split(File.pathSeparator)
    }

    private fun assertShortensEverything(parameters: JavaParameters) {
        assertTrue("classpath", parameters.isDynamicClasspath)
        assertTrue("program parameters", parameters.isDynamicParameters)
        assertTrue("vm options", parameters.isDynamicVMOptions)
    }

    private fun assertOnlyClasspathIsShortened(parameters: JavaParameters) {
        assertTrue("classpath", parameters.isDynamicClasspath)
        assertFalse("program parameters", parameters.isDynamicParameters)
        assertFalse("vm options", parameters.isDynamicVMOptions)
    }

    private fun configuration(
        scriptFile: File = File(FileUtil.createTempDirectory(getTestName(true), null, true), "script.kts"),
    ): KotlinStandaloneScriptRunConfiguration {
        scriptFile.writeText("println(\"hello\")")
        checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(scriptFile)) {
            "Failed to find the script file in VFS: $scriptFile"
        }

        return KotlinStandaloneScriptRunConfiguration(
            project, kotlinStandaloneScriptRunConfigurationType(), "script.kts"
        ).apply { filePath = scriptFile.path }
    }

    private fun javaParameters(shortenCommandLine: ShortenCommandLine?): JavaParameters =
        javaParameters(configuration().apply { setShortenCommandLine(shortenCommandLine) })

    private fun javaParameters(configuration: KotlinStandaloneScriptRunConfiguration): JavaParameters {
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = ExecutionEnvironmentBuilder.create(project, executor, configuration).build()
        val state = configuration.getState(executor, environment) as JavaCommandLineState
        return state.javaParameters
    }
}
