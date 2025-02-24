// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.io.write
import com.intellij.util.lang.JavaVersion
import org.jdom.Element
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingPassBase
import org.jetbrains.kotlin.idea.intentions.computeOnBackground
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.AFTER_ERROR_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.DISABLE_ERRORS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.psi.KtFile
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

    private val noFixTextDirectiveName: String = "NO_FIX"

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
                "$inspectionFileName file is not found for " + testDataFile +
                        "\nAdd it to base directory of test data. It should contain fully-qualified name of inspection class."
            )
        }
        if (candidateFiles.size > 1) {
            throw AssertionError(
                "Several $inspectionFileName files are available for " + testDataFile +
                        "\nPlease remove some of them\n" + candidateFiles
            )
        }

        val className = FileUtil.loadFile(candidateFiles[0]).trim { it <= ' ' }
        return Class.forName(className).getDeclaredConstructor().newInstance() as LocalInspectionTool
    }

    protected open fun doTest(path: String) {
        val mainFile = File(dataFilePath(fileName()))

        val inspection: LocalInspectionTool = try {
            createInspection(mainFile)
        } catch (e: Throwable) {
            val shouldBeIgnored =
                InTextDirectivesUtils.isDirectiveDefined(FileUtil.loadFile(mainFile), IgnoreTests.DIRECTIVES.of(pluginMode))
            if (shouldBeIgnored) {
                return
            } else {
                throw e
            }
        }

        val fileText = FileUtil.loadFile(mainFile, true)
        assertTrue("\"<caret>\" is missing in file \"$mainFile\"", fileText.contains("<caret>"))

        withCustomCompilerOptions(fileText, project, module) {
            val minJavaVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// MIN_JAVA_VERSION: ")?.toInt()
            if (minJavaVersion != null && !JavaVersion.current().isAtLeast(minJavaVersion)) {
                return@withCustomCompilerOptions
            }


            val extraFileNames = findExtraFilesForTest(mainFile)

            myFixture.configureByFiles(*(listOf(mainFile.name) + extraFileNames).toTypedArray()).first()

            val ktFile = myFixture.file as KtFile
            if (ktFile.isScript()) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(ktFile)
            }
            checkForUnexpectedErrors(mainFile, ktFile, fileText, beforeCheck = true)

            doTestFor(mainFile, inspection, fileText)

            PsiTestUtil.checkPsiStructureWithCommit(file, PsiTestUtil::checkPsiMatchesTextIgnoringNonCode)
        }
    }

    /**
      For each test file `xxx.foo` there can be several "extra" files configured, to be copied to the same project with the main file.
      These "extra" file names should be of the form: "xxx.1.blah", "xxx.2.foo", "xxx.3.xml" etc.
      I.e., they should start with the main file name, followed by sequential number 1,2,3..., followed by any extension.
     */
    protected fun findExtraFilesForTest(mainFile: File): List<String> {
        var i = 1
        val extraFileNames = mutableListOf<String>()
        extraFileLoop@ while (true) {
            val extra = File(mainFile.parent).listFiles { _, name ->
                    name.startsWith(FileUtil.getNameWithoutExtension(mainFile) + "." + i + ".")
                    && !name.endsWith(".after")
            }
            if (extra != null && extra.size == 1) {
                val extraFile = extra[0]
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
        return extraFileNames
    }

    private fun checkForUnexpectedErrors(mainFile: File, ktFile: KtFile, fileText: String, beforeCheck: Boolean) {
        if (beforeCheck) {
            val skipErrorsBeforeCheck = InTextDirectivesUtils.findLinesWithPrefixesRemoved(
                fileText,
                *skipErrorsBeforeCheckDirectives.toTypedArray()
            ).isNotEmpty()
            if (!skipErrorsBeforeCheck) {
                checkForErrorsBefore(mainFile, ktFile, fileText)
            }
        } else {
            val skipErrorsAfterCheck = InTextDirectivesUtils.findLinesWithPrefixesRemoved(
                fileText,
                *skipErrorsAfterCheckDirectives.toTypedArray()
            ).isNotEmpty()
            if (!skipErrorsAfterCheck) {
                checkForErrorsAfter(mainFile, ktFile, fileText)
            }
        }
    }

    protected open val skipErrorsBeforeCheckDirectives: List<String> =
        listOf(IgnoreTests.DIRECTIVES.of(pluginMode), DISABLE_ERRORS_DIRECTIVE, "// SKIP_ERRORS_BEFORE")

    protected open val skipErrorsAfterCheckDirectives: List<String> =
        listOf(IgnoreTests.DIRECTIVES.of(pluginMode), DISABLE_ERRORS_DIRECTIVE, "// SKIP_ERRORS_AFTER")

    protected open fun checkForErrorsBefore(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile)
    }

    protected open fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile, directive = AFTER_ERROR_DIRECTIVE)
    }

    protected fun runInspectionWithFixesAndCheck(
        inspection: LocalInspectionTool,
        expectedProblemString: String?,
        expectedHighlightString: String?,
        localFixTextString: String?,
        inspectionSettings: Element?,
        noLocalFixTextStrings: List<String> = emptyList(),
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

        val message = if (problemExpected)
            "Expected at least one problem at caret, but got none"
        else
            "No problems should have been detected at caret, but got ${highlightInfos.size} problems:\n " +
                    "${highlightInfos.joinToString(separator = "\n")}"
        assertTrue(message, problemExpected == highlightInfos.isNotEmpty())

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

        val allLocalFixActions: MutableList<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> = mutableListOf()
        highlightInfos.forEach { info ->
            info.findRegisteredQuickFix<Any?> { desc, fixRange ->
                allLocalFixActions.add(desc to fixRange)
                null
            }
        }

        if (allLocalFixActions.isNotEmpty()) {
            val actions = allLocalFixActions.map { it.first.action.text }
            noLocalFixTextStrings.forEach {
                assertTrue(
                    "Expected no `$it` fix action",
                    it !in actions
                )
            }
        }

        val localFixActions = if (localFixTextString == null || localFixTextString == "none") {
            allLocalFixActions
        } else {
            allLocalFixActions
                .filter { fix -> fix.first.action.text == localFixTextString }
                .selectActionsWithMostSpecificRanges()
        }

        val availableDescription = allLocalFixActions.joinToString { "'${it.first.action.text}'" }

        val fixDescription = localFixTextString?.let { "with specified text '$localFixTextString'" } ?: ""
        if (localFixTextString != "none") {
            assertTrue("Fix '$fixDescription' not found among ${allLocalFixActions.size} actions available:\n $availableDescription",
              localFixActions.isNotEmpty()
            )
        }

        val localFixAction = localFixActions.singleOrNull { it.first.action !is EmptyIntentionAction }?.first?.action
        if (localFixTextString == "none") {
            assertTrue("Expected no fix action, actual: `${localFixAction?.text}`", localFixAction == null)
            return false
        }
        assertTrue(
            "More than one fix action $fixDescription\n" +
                    "Available actions: $availableDescription",
            localFixAction != null
        )

        val modCommandAction = localFixAction!!.asModCommandAction()
        if (modCommandAction != null) {
            val actionContext = ActionContext.from(editor, file)
            val command: ModCommand = project.computeOnBackground {
                runReadAction {
                    modCommandAction.perform(actionContext)
                }
            }
            project.executeCommand(localFixAction.text, null) {
                ModCommandExecutor.getInstance().executeInteractively(actionContext, command, editor)
            }
        } else {
            project.executeCommand(localFixAction.text, null) {
                if (localFixAction.startInWriteAction()) {
                    runWriteAction { localFixAction.invoke(project, editor, file) }
                } else {
                    localFixAction.invoke(project, editor, file)
                }
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

    /**
     * Selects and returns a list of [HighlightInfo.IntentionActionDescriptor] with the most specific ranges.
     *
     * The method sorts the given actions by their start and end offsets, ensuring that within
     * equal start offsets, the smaller range is preferred.
     * It then iterates through the sorted list to remove any actions whose ranges are fully
     * contained within the ranges of other actions, effectively choosing the most specific
     * range for each set of overlapping ranges.
     */
    private fun List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>>.selectActionsWithMostSpecificRanges(): List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> {
        val originalActions = this

        val sortedActions = originalActions
            .sortedWith(compareBy({ it.second.startOffset }, { it.second.endOffset })) // sort by ranges
            .distinctBy { it.second.startOffset } // if start offsets are equals, the first range is the best possible

        val mostSpecificActions = mutableListOf<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>>()

        for (action in sortedActions) {
            val last = mostSpecificActions.lastOrNull()

            if (last == null) {
                mostSpecificActions += action
                continue
            }

            if (last.second.contains(action.second)) {
                // this range is more specific
                mostSpecificActions.removeLast()
                mostSpecificActions += action
            } else {
                require(last.second.startOffset < action.second.startOffset)
                require(last.second.endOffset < action.second.endOffset)

                // this range only intersects with the previous; it should be considered on its own
                mostSpecificActions += action
            }
        }

        return mostSpecificActions
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
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.of(pluginMode), "after") {
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
        val noLocalFixTextStrings = InTextDirectivesUtils.findListWithPrefixes(
            fileText, "// $noFixTextDirectiveName: "
        )

        val inspectionSettings = loadInspectionSettings(mainFile)
        val afterFileAbsolutePath = getAfterTestDataAbsolutePath(mainFileName)

        if (!runInspectionWithFixesAndCheck(
                inspection,
                expectedProblemString,
                expectedHighlightString,
                localFixTextString,
                inspectionSettings,
                noLocalFixTextStrings,
            )
        ) {
            assertFalse("${afterFileAbsolutePath.fileName} should not exist as no action could be applied:\n$afterFileAbsolutePath", Files.exists(afterFileAbsolutePath))
            return
        }

        createAfterFileIfItDoesNotExist(afterFileAbsolutePath)
        dispatchAllEventsInIdeEventQueue()
        try {
            myFixture.checkResultByFile("${afterFileAbsolutePath.fileName}")
        } catch (_: FileComparisonFailedError) {
            KotlinTestUtils.assertEqualsToFile(
                File(testDataDirectory, "${afterFileAbsolutePath.fileName}"),
                editor.document.text
            )
        }

        checkForUnexpectedErrors(mainFile, file as KtFile, fileText, beforeCheck = false)
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
}
