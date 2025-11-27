// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.registerExtension
import org.jdom.Element
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.runConfigurations.jvm.script.KotlinStandaloneScriptRunConfiguration
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.test.assertNotEquals

@TestRoot("idea/tests")
@TestMetadata("testData/run/StandaloneScript")
@RunWith(JUnit38ClassRunner::class)
class StandaloneScriptRunConfigurationTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
            override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk18()
        }
    }

    override fun setUp() = setUpWithKotlinPlugin {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(projectDescriptor, name).fixture
        myFixture = JavaTestFixtureFactory.getFixtureFactory()
            .createCodeInsightFixture(fixture).apply {
                setTestDataPath(testDataDirectory.path)
                setUp()
            }
    }

    private fun assertEqualPaths(expected: String?, actual: String?) {
        assertEquals(
            expected?.let { FileUtilRt.toSystemIndependentName(it) },
            actual?.let { FileUtilRt.toSystemIndependentName(it) }
        )
    }

    private fun assertNotEqualPaths(illegal: String?, actual: String?, message: String? = null) {
        assertNotEquals(
            illegal?.let { FileUtilRt.toSystemIndependentName(it) },
            actual?.let { FileUtilRt.toSystemIndependentName(it) },
            message
        )
    }

    fun testConfigurationForScript() {
        val script = myFixture.configureByFile("run/simpleScript.kts")
        val runConfiguration = createConfigurationFromElement(script) as KotlinStandaloneScriptRunConfiguration

        assertEqualPaths(script.containingFile.virtualFile.canonicalPath, runConfiguration.filePath)
        Assert.assertEquals(
            runConfiguration.filePath?.let { FileUtilRt.toSystemIndependentName(it) },
            runConfiguration.systemIndependentPath
        )

        Assert.assertEquals("simpleScript.kts", runConfiguration.name)

        assertTrue(runConfiguration.toXmlString().contains(Regex("""<option name="filePath" value="[^"]+simpleScript.kts" />""")))

        val javaParameters = getJavaRunParameters(runConfiguration)
        val programParametersList = javaParameters.programParametersList.list

        programParametersList.checkParameter("-script") { it.contains("simpleScript.kts") }
        programParametersList.checkParameter("-kotlin-home") { it == KotlinPluginLayout.kotlinc.absolutePath }

        assertTrue(programParametersList.contains("plugin:kotlin.scripting:script-definitions=kotlin.script.templates.standard.ScriptTemplateWithArgs"))
        assertTrue(!programParametersList.contains("-cp"))
    }

    fun testConfigurationForScriptWithCustomDefinition() {
        project.registerExtension(SCRIPT_DEFINITIONS_SOURCES, TestDefinitionSource(), testRootDisposable)

        val script = myFixture.configureByFile("run/simpleScript.kts")
        val runConfiguration = createConfigurationFromElement(script) as KotlinStandaloneScriptRunConfiguration

        assertEqualPaths(script.containingFile.virtualFile.canonicalPath, runConfiguration.filePath)
        Assert.assertEquals(
            runConfiguration.filePath?.let { FileUtilRt.toSystemIndependentName(it) },
            runConfiguration.systemIndependentPath
        )

        Assert.assertEquals("simpleScript.kts", runConfiguration.name)

        assertTrue(runConfiguration.toXmlString().contains(Regex("""<option name="filePath" value="[^"]+simpleScript.kts" />""")))

        val javaParameters = getJavaRunParameters(runConfiguration)
        val programParametersList = javaParameters.programParametersList.list

        programParametersList.checkParameter("-script") { it.contains("simpleScript.kts") }
        programParametersList.checkParameter("-kotlin-home") { it == KotlinPluginLayout.kotlinc.absolutePath }

        assertTrue(programParametersList.contains("plugin:kotlin.scripting:script-definitions=org.jetbrains.kotlin.idea.run.StandaloneScriptRunConfigurationTest.MyCustomBaseClass"))
        assertTrue(!programParametersList.contains("-cp"))
    }

    private class TestDefinitionSource : ScriptDefinitionsSource {
        override val definitions: Sequence<ScriptDefinition>
            get() = sequenceOf(
                object : ScriptDefinition.FromConfigurations(
                    ScriptingHostConfiguration {},
                    ScriptCompilationConfiguration {},
                    null
                ) {
                    init {
                        order = Int.MIN_VALUE
                    }

                    override val baseClassType: KotlinType
                        get() = KotlinType(MyCustomBaseClass::class)
                })
    }

    class MyCustomBaseClass

    fun testOnFileRename() {
        val script = myFixture.configureByFile("renameFile/simpleScript.kts")
        val runConfiguration = createConfigurationFromElement(script, save = true) as KotlinStandaloneScriptRunConfiguration

        Assert.assertEquals("simpleScript.kts", runConfiguration.name)
        val scriptVirtualFileBefore = script.containingFile.virtualFile
        val originalPath = scriptVirtualFileBefore.canonicalPath
        val originalWorkingDirectory = scriptVirtualFileBefore.parent.canonicalPath
        assertEqualPaths(originalPath, runConfiguration.filePath)
        assertEqualPaths(originalWorkingDirectory, runConfiguration.workingDirectory)

        RefactoringFactory.getInstance(project).createRename(script.containingFile, "renamedScript.kts").run()

        Assert.assertEquals("renamedScript.kts", runConfiguration.name)
        val scriptVirtualFileAfter = script.containingFile.virtualFile

        assertEqualPaths(scriptVirtualFileAfter.canonicalPath, runConfiguration.filePath)
        assertNotEqualPaths(originalPath, runConfiguration.filePath)

        assertEqualPaths(scriptVirtualFileAfter.parent.canonicalPath, runConfiguration.workingDirectory)
        assertEqualPaths(originalWorkingDirectory, runConfiguration.workingDirectory)
    }

    fun testOnFileMoveWithDefaultWorkingDir() {
        val script = myFixture.configureByFile("move/script.kts")
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(script)

        val runConfiguration = createConfigurationFromElement(script, save = true) as KotlinStandaloneScriptRunConfiguration

        Assert.assertEquals("script.kts", runConfiguration.name)
        val scriptVirtualFileBefore = script.containingFile.virtualFile
        val originalPath = scriptVirtualFileBefore.canonicalPath
        val originalWorkingDirectory = scriptVirtualFileBefore.parent.canonicalPath
        assertEqualPaths(originalPath, runConfiguration.filePath)
        assertEqualPaths(originalWorkingDirectory, runConfiguration.workingDirectory)

        moveScriptFile(script.containingFile)

        Assert.assertEquals("script.kts", runConfiguration.name)
        val scriptVirtualFileAfter = script.containingFile.virtualFile

        assertEqualPaths(scriptVirtualFileAfter.canonicalPath, runConfiguration.filePath)
        assertNotEqualPaths(originalPath, runConfiguration.filePath)

        assertEqualPaths(scriptVirtualFileAfter.parent.canonicalPath, runConfiguration.workingDirectory)
        assertNotEqualPaths(originalWorkingDirectory, runConfiguration.workingDirectory)
    }

    fun testOnFileMoveWithNonDefaultWorkingDir() {
        val script = myFixture.configureByFile("move/script.kts")

        ScriptConfigurationManager.updateScriptDependenciesSynchronously(script)

        val runConfiguration = createConfigurationFromElement(script, save = true) as KotlinStandaloneScriptRunConfiguration

        Assert.assertEquals("script.kts", runConfiguration.name)
        runConfiguration.workingDirectory += "/customWorkingDirectory"
        val scriptVirtualFileBefore = script.containingFile.virtualFile
        val originalPath = scriptVirtualFileBefore.canonicalPath
        val originalWorkingDirectory = scriptVirtualFileBefore.parent.canonicalPath + "/customWorkingDirectory"

        assertEqualPaths(originalPath, runConfiguration.filePath)
        assertEqualPaths(originalWorkingDirectory, runConfiguration.workingDirectory)

        moveScriptFile(script)

        Assert.assertEquals("script.kts", runConfiguration.name)
        val scriptVirtualFileAfter = script.containingFile.virtualFile

        assertEqualPaths(scriptVirtualFileAfter.canonicalPath, runConfiguration.filePath)
        assertNotEqualPaths(originalPath, runConfiguration.filePath)

        assertNotEqualPaths(scriptVirtualFileAfter.parent.canonicalPath, runConfiguration.workingDirectory)
        assertEqualPaths(originalWorkingDirectory, runConfiguration.workingDirectory)
    }

    private fun List<String>.checkParameter(name: String, condition: (String) -> Boolean) {
        val param = find { it == name } ?: throw AssertionError("Should pass $name to compiler")
        val paramValue = this[this.indexOf(param) + 1]
        assertTrue("Check for $name parameter fails: actual value = $paramValue", condition(paramValue))
    }


    fun moveScriptFile(scriptFile: PsiFile) {
        MoveFilesOrDirectoriesProcessor(
            project,
            arrayOf(scriptFile),
            myFixture.getPsiManager().findDirectory(myFixture.getTempDirFixture().findOrCreateDir("dest"))!!,
            false, true, null, null
        ).run()
    }

    private fun RunConfiguration.toXmlString(): String {
        val element = Element("temp")
        writeExternal(element)
        return JDOMUtil.writeElement(element)
    }
}
