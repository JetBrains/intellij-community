// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.rt.execution.junit.FileComparisonData
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.io.write
import com.intellij.util.lang.JavaVersion
import junit.framework.TestCase
import org.jdom.Element
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingPassBase
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.div


abstract class AbstractLocalInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
    protected open val inspectionFileName: String
        get() = ".inspection"

    private val afterFileNameSuffix: String = ".after"

    private val expectedProblemDirectiveName: String = "PROBLEM"

    protected val expectedProblemHighlightType: String = "HIGHLIGHT"

    private val fixTextDirectiveName: String = "FIX"

    private fun createInspection(testDataFile: File): LocalInspectionTool {
        val candidateFiles = mutableListOf<File>()

        var current: File? = testDataFile.parentFile
        while (current != null) {
            val candidate = File(current, inspectionFileName)
            if (candidate.exists()) {
                candidateFiles.add(candidate)
            }
            current = current.parentFile
        }

        if (candidateFiles.isEmpty()) {
            throw AssertionError(
                ".inspection file is not found for " + testDataFile +
                        "\nAdd it to base directory of test data. It should contain fully-qualified name of inspection class."
            )
        }
        if (candidateFiles.size > 1) {
            throw AssertionError(
                "Several .inspection files are available for " + testDataFile +
                        "\nPlease remove some of them\n" + candidateFiles
            )
        }

        val className = FileUtil.loadFile(candidateFiles[0]).trim { it <= ' ' }
        return Class.forName(className).getDeclaredConstructor().newInstance() as LocalInspectionTool
    }

    protected open fun doTest(path: String) {
        val mainFile = File(path)
        val inspection = createInspection(mainFile)

        val fileText = FileUtil.loadFile(mainFile, true)
        TestCase.assertTrue("\"<caret>\" is missing in file \"$mainFile\"", fileText.contains("<caret>"))

        withCustomCompilerOptions(fileText, project, module) {
            val minJavaVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// MIN_JAVA_VERSION: ")?.toInt()
            if (minJavaVersion != null && !JavaVersion.current().isAtLeast(minJavaVersion)) {
                return@withCustomCompilerOptions
            }

            checkForUnexpectedErrors()

            var i = 1
            val extraFileNames = mutableListOf<String>()
            extraFileLoop@ while (true) {
                for (extension in EXTENSIONS) {
                    val extraFile = File(mainFile.parent, FileUtil.getNameWithoutExtension(mainFile) + "." + i + extension)
                    if (extraFile.exists()) {
                        extraFileNames += extraFile.name
                        i++
                        continue@extraFileLoop
                    }
                }
                break
            }
            val parentFile = mainFile.parentFile
            if (parentFile != null) {
                for (file in parentFile.walkTopDown().maxDepth(1)) {
                    if (file.name.endsWith(".lib.kt")) {
                        extraFileNames += file.name
                    }
                }
            }

            myFixture.configureByFiles(*(listOf(mainFile.name) + extraFileNames).toTypedArray()).first()

            if ((myFixture.file as? KtFile)?.isScript() == true) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
            }

            doTestFor(mainFile, inspection, fileText)

            PsiTestUtil.checkPsiStructureWithCommit(file, PsiTestUtil::checkPsiMatchesTextIgnoringNonCode)
        }
    }

    private fun checkForUnexpectedErrors() {
        val ktFile = file as? KtFile ?: return
        val fileText = ktFile.text
        if (!InTextDirectivesUtils.isDirectiveDefined(fileText, "// SKIP_ERRORS_AFTER")) {
            checkForUnexpectedErrors(fileText)
        }
    }

    protected open fun checkForUnexpectedErrors(fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(file as KtFile)
    }

    protected fun runInspectionWithFixesAndCheck(
        inspection: LocalInspectionTool,
        expectedProblemString: String?,
        expectedHighlightString: String?,
        localFixTextString: String?,
        inspectionSettings: Element? = null
    ): Boolean {
        val problemExpected = expectedProblemString == null || expectedProblemString != "none"
        // use Class instead of `inspection` as an argument to correctly calculate LocalInspectionToolWrapper.getID()
        // (because "suppressID" can be hardcoded in plugin.xml the just created LocalInspectionTool knows nothing about)
        myFixture.enableInspections(inspection::class.java)

        // Set default level to WARNING to make possible to test DO_NOT_SHOW
        val inspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
        val inspectionProfile = inspectionProfileManager.currentProfile
        val state = inspectionProfile.getToolDefaultState(inspection.shortName, project)
        state.level = HighlightDisplayLevel.WARNING

        if (inspectionSettings != null) {
            state.tool.tool.readSettings(inspectionSettings)
        }

        val highlightInfos = collectHighlightInfos()

        assertTrue(
            if (!problemExpected)
                "No problems should be detected at caret\n" +
                        "Detected problems: ${highlightInfos.joinToString { it.description }}"
            else
                "Expected at least one problem at caret",
            problemExpected == highlightInfos.isNotEmpty()
        )

        if (!problemExpected || highlightInfos.isEmpty()) return false

        if (expectedProblemString != null) {
            assertTrue(
                "Expected the following problem at caret: $expectedProblemString\n" +
                        "Active problems: ${highlightInfos.joinToString(separator = "\n") { it.description }}",
                highlightInfos.any { it.description == expectedProblemString }
            )
        }
        val expectedHighlightType = when (expectedHighlightString) {
            null -> null
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING.name -> HighlightDisplayLevel.WARNING.name
            else -> expectedHighlightString
        }
        if (expectedHighlightType != null) {
            assertTrue(
                "Expected the following problem highlight type: $expectedHighlightType\n" +
                        "Actual type: ${highlightInfos.joinToString { it.type.toString() }}",
                highlightInfos.all { expectedHighlightType in it.type.toString() }
            )
        }

        val allLocalFixActions:MutableList<IntentionAction> = ArrayList()
        highlightInfos.forEach { info ->
            info.findRegisteredQuickFix<Any?> { desc, _ ->
                allLocalFixActions.add(desc.action)
                null
            }
        }

        val localFixActions = if (localFixTextString == null || localFixTextString == "none") {
            allLocalFixActions
        } else {
            allLocalFixActions.filter { fix -> fix.text == localFixTextString }
        }

        val availableDescription = allLocalFixActions.joinToString { "'${it.text}'" }

        val fixDescription = localFixTextString?.let { "with specified text '$localFixTextString'" } ?: ""
        if (localFixTextString != "none") {
            assertTrue(
              "Fix $fixDescription not found in actions available:\n $availableDescription",
              localFixActions.isNotEmpty()
            )
        }

        val localFixAction = localFixActions.singleOrNull { it !is EmptyIntentionAction }
        if (localFixTextString == "none") {
            assertTrue("Expected no fix action", localFixAction == null)
            return false
        }
        assertTrue(
            "More than one fix action $fixDescription\n" +
                    "Available actions: $availableDescription",
            localFixAction != null
        )

        project.executeCommand(localFixAction!!.text, null) {
            if (localFixAction.startInWriteAction()) {
                runWriteAction { localFixAction.invoke(project, editor, file) }
            } else {
                localFixAction.invoke(project, editor, file)
            }
        }
        return true
    }

    protected open fun collectHighlightInfos(): List<HighlightInfo> {
        val passIdsToIgnore = passesToIgnore()

        val caretOffset = myFixture.caretOffset

        // exclude AbstractHighlightingPassBase-derived passes in tests
        return AbstractHighlightingPassBase.ignoreThesePassesInTests {
            CodeInsightTestFixtureImpl.instantiateAndRun(
                file, editor, passIdsToIgnore, (file as? KtFile)?.isScript() == true
            ).filter { it.description != null && caretOffset in it.startOffset..it.endOffset }
        }
    }

    protected open fun passesToIgnore(): IntArray {
        return intArrayOf(
            Pass.LINE_MARKERS,
            Pass.SLOW_LINE_MARKERS,
            Pass.EXTERNAL_TOOLS,
            Pass.POPUP_HINTS,
            Pass.UPDATE_ALL,
            Pass.UPDATE_FOLDING,
            Pass.WOLF
        )
    }

    protected open fun doTestFor(mainFile: File, inspection: LocalInspectionTool, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_K1, "after") {
            doTestForInternal(mainFile, inspection, fileText)
        }
    }

    protected open fun getAfterTestDataAbsolutePath(mainFileName: String) =
        testDataDirectory.toPath() / (mainFileName + afterFileNameSuffix)

    protected fun doTestForInternal(mainFile: File, inspection: LocalInspectionTool, fileText: String) {
        val mainFileName = mainFile.name
        val expectedProblemString = InTextDirectivesUtils.findStringWithPrefixes(
            fileText, "// $expectedProblemDirectiveName: "
        )
        val expectedHighlightString = InTextDirectivesUtils.findStringWithPrefixes(
            fileText, "// $expectedProblemHighlightType: "
        )
        val localFixTextString = InTextDirectivesUtils.findStringWithPrefixes(
            fileText, "// $fixTextDirectiveName: "
        )

        val inspectionSettings = loadInspectionSettings(mainFile)
        val afterFileAbsolutePath = getAfterTestDataAbsolutePath(mainFileName)

        if (!runInspectionWithFixesAndCheck(
                inspection,
                expectedProblemString,
                expectedHighlightString,
                localFixTextString,
                inspectionSettings
            )
        ) {
            assertFalse("${afterFileAbsolutePath.fileName} should not exist as no action could be applied", Files.exists(afterFileAbsolutePath))
            return
        }

        createAfterFileIfItDoesNotExist(afterFileAbsolutePath)
        dispatchAllEventsInIdeEventQueue()
        try {
            myFixture.checkResultByFile("${afterFileAbsolutePath.fileName}")
        } catch (e: AssertionError) {
            if (e !is FileComparisonData) throw e
            KotlinTestUtils.assertEqualsToFile(
                File(testDataDirectory, "${afterFileAbsolutePath.fileName}"),
                editor.document.text
            )
        }

        checkForUnexpectedErrors()
    }

    private fun createAfterFileIfItDoesNotExist(path: Path) {
        if (!Files.exists(path)) {
            path.createFile().write(editor.document.text)
            error("File $path was not found and thus was generated")
        }
    }

    protected fun loadInspectionSettings(testFile: File): Element? =
        File(testFile.parentFile, "settings.xml")
            .takeIf { it.exists() }
            ?.let { JDOMUtil.load(it) }

    companion object {
        private val EXTENSIONS = arrayOf(".kt", ".kts", ".java", ".groovy")
    }
}
