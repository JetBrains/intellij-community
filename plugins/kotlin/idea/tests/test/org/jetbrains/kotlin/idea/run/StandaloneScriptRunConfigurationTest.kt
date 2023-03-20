// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.ActionRunner
import org.jdom.Element
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.run.script.standalone.KotlinStandaloneScriptRunConfiguration
import org.jetbrains.kotlin.idea.stubindex.KotlinScriptFqnIndex
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinCodeInsightTestCase
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import kotlin.test.assertNotEquals

@RunWith(JUnit38ClassRunner::class)
class StandaloneScriptRunConfigurationTest : KotlinCodeInsightTestCase() {

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
        configureByFile("run/simpleScript.kts")
        val script = KotlinScriptFqnIndex["foo.SimpleScript", project, project.allScope()].single()
        val runConfiguration = createConfigurationFromElement(script) as KotlinStandaloneScriptRunConfiguration

        assertEqualPaths(script.containingFile.virtualFile.canonicalPath, runConfiguration.filePath)
        Assert.assertEquals(
            runConfiguration.filePath?.let { FileUtilRt.toSystemIndependentName(it) },
            runConfiguration.systemIndependentPath
        )

        Assert.assertEquals("simpleScript.kts", runConfiguration.name)

        Assert.assertTrue(runConfiguration.toXmlString().contains(Regex("""<option name="filePath" value="[^"]+simpleScript.kts" />""")))

        val javaParameters = getJavaRunParameters(runConfiguration)
        val programParametersList = javaParameters.programParametersList.list

        programParametersList.checkParameter("-script") { it.contains("simpleScript.kts") }
        programParametersList.checkParameter("-kotlin-home") { it == KotlinPluginLayout.kotlinc.absolutePath }

        Assert.assertTrue(!programParametersList.contains("-cp"))

    }

    fun testOnFileRename() {
        configureByFile("renameFile/simpleScript.kts")
        val script = KotlinScriptFqnIndex["foo.SimpleScript", project, project.allScope()].single()
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
        configureByFile("move/script.kts")

        ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFile)

        val script = KotlinScriptFqnIndex["foo.Script", project, project.allScope()].single()
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
        configureByFile("move/script.kts")

        ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFile)

        val script = KotlinScriptFqnIndex["foo.Script", project, project.allScope()].single()
        val runConfiguration = createConfigurationFromElement(script, save = true) as KotlinStandaloneScriptRunConfiguration

        Assert.assertEquals("script.kts", runConfiguration.name)
        runConfiguration.workingDirectory = runConfiguration.workingDirectory + "/customWorkingDirectory"
        val scriptVirtualFileBefore = script.containingFile.virtualFile
        val originalPath = scriptVirtualFileBefore.canonicalPath
        val originalWorkingDirectory = scriptVirtualFileBefore.parent.canonicalPath + "/customWorkingDirectory"

        assertEqualPaths(originalPath, runConfiguration.filePath)
        assertEqualPaths(originalWorkingDirectory, runConfiguration.workingDirectory)

        moveScriptFile(script.containingFile)

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
        Assert.assertTrue("Check for $name parameter fails: actual value = $paramValue", condition(paramValue))
    }

    fun moveScriptFile(scriptFile: PsiFile) {
        ActionRunner.runInsideWriteAction { VfsUtil.createDirectoryIfMissing(scriptFile.virtualFile.parent, "dest") }

        MoveFilesOrDirectoriesProcessor(
            project,
            arrayOf(scriptFile),
            JavaPsiFacade.getInstance(project).findPackage("dest")!!.directories[0],
            false, true, null, null
        ).run()
    }

    private fun RunConfiguration.toXmlString(): String {
        val element = Element("temp")
        writeExternal(element)
        return JDOMUtil.writeElement(element)
    }

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("run/StandaloneScript")
    override fun getTestProjectJdk() = IdeaTestUtil.getMockJdk18()
}
