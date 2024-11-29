// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.caches.resolve.ResolveInDispatchThreadException
import org.jetbrains.kotlin.idea.caches.resolve.forceCheckForResolveInDispatchThreadInTests
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.statistic.FilterableTestStatisticsEventLoggerProvider
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import org.junit.ComparisonFailure
import java.io.File
import java.nio.file.Paths

abstract class AbstractQuickFixTest : KotlinLightCodeInsightFixtureTestCase(), QuickFixTest {
    companion object {
        const val APPLY_QUICKFIX_DIRECTIVE = "APPLY_QUICKFIX"
        const val ACTION_DIRECTIVE = "ACTION"
        const val SHOULD_BE_AVAILABLE_AFTER_EXECUTION_DIRECTIVE = "SHOULD_BE_AVAILABLE_AFTER_EXECUTION"
        const val FIXTURE_CLASS_DIRECTIVE = "FIXTURE_CLASS"
        const val SHOULD_FAIL_WITH_DIRECTIVE = "SHOULD_FAIL_WITH"
        const val FORCE_PACKAGE_FOLDER_DIRECTIVE = "FORCE_PACKAGE_FOLDER"
        const val K1_TOOL_DIRECTIVE = "K1_TOOL:"
        const val K2_TOOL_DIRECTIVE = "K2_TOOL:"

        private val quickFixesAllowedToResolveInWriteAction = AllowedToResolveUnderWriteActionData(
            IDEA_TEST_DATA_DIR.resolve("quickfix/allowResolveInWriteAction.txt").path,
            """
                    # Actions that are allowed to resolve in write action. Normally this list shouldn't be extended and eventually should
                    # be dropped. Please consider rewriting a quick-fix and remove resolve from it before adding a new entry to this list.
            """.trimIndent(),
        )

        private fun unwrapIntention(action: IntentionAction): Any {
            val original = IntentionActionDelegate.unwrap(action)
            return QuickFixWrapper.unwrap(original) ?: original
        }
    }

    private var statisticDisposable: Disposable? = null
    protected var statisticsEventLoggerProvider: FilterableTestStatisticsEventLoggerProvider? = null
    private var actionHint: ActionHint? = null

    override fun setUp() {
        super.setUp()
        val newDisposable = Disposer.newDisposable(testRootDisposable, "statisticTestDisposable")
        statisticDisposable = newDisposable

        val loggerProvider = FilterableTestStatisticsEventLoggerProvider("FUS") { it == "called" }
        statisticsEventLoggerProvider = loggerProvider
        StatisticsEventLoggerProvider.EP_NAME.point.registerExtension(
            loggerProvider,
            LoadingOrder.FIRST,
            newDisposable
        )
        (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    }

    override fun tearDown() {
        runAll(
            {
                actionHint = null
                statisticDisposable?.let(Disposer::dispose)
                statisticDisposable = null
            },
            { super.tearDown() },
        )
    }

    protected open val disableTestDirective: String
        get() = IgnoreTests.DIRECTIVES.of(pluginMode)

    override fun runInDispatchThread(): Boolean = false

    protected open fun doTest(beforeFileName: String) {
        val beforeFile = File(beforeFileName)
        val beforeFileText = FileUtil.loadFile(beforeFile)
        InTextDirectivesUtils.checkIfMuted(beforeFileText)
        configureRegistryAndRun(project, beforeFileText) {
            withCustomCompilerOptions(beforeFileText, project, module) {
                IgnoreTests.runTestIfNotDisabledByFileDirective(Paths.get(beforeFileName), disableTestDirective) {
                    val inspections = parseInspectionsToEnable(beforeFileName, beforeFileText).toTypedArray()

                    try {
                        KotlinTestHelpers.registerChooserInterceptor(myFixture.testRootDisposable)
                        myFixture.enableInspections(*inspections)

                        doKotlinQuickFixTest(beforeFileName)
                        runInEdtAndWait { checkForUnexpectedErrors() }
                    } finally {
                        myFixture.disableInspections(*inspections)
                    }
                }

                // if `disableTestDirective` is present in the file and `runTestIfNotDisabledByFileDirective` doesn't throw an exception
                // (meaning that the test indeed doesn't pass), don't run other checks
                if (beforeFileText.lines().none { it.startsWith(disableTestDirective) }) {
                    runInEdtAndWait {
                        checkFusEvents(beforeFile, beforeFileText)
                        PsiTestUtil.checkPsiStructureWithCommit(file, PsiTestUtil::checkPsiMatchesTextIgnoringNonCode)
                    }
                }
            }
        }
    }

    private fun checkFusEvents(file: File, fileText: String) {
        val calledEventIds = statisticsEventLoggerProvider!!.getLoggedEvents()
            .map { it.event }
            .filter {
                it.id == "called"
            }
            .map {
                it.data["id"]
            }
        val applyQuickFix = applyQuickFix()
        if (actionHint?.shouldPresent() == false || !applyQuickFix) {
            assertTrue("no `called` event should happen: $calledEventIds", calledEventIds.isEmpty())
            return
        }
        //multiple, when chooser is provided
        val calledId = calledEventIds.firstOrNull() as? String ?: error("single `called` event is expected: $calledEventIds")

        val fusDirectiveName = if (isFirPlugin) {
            "FUS_K2_QUICKFIX_NAME"
        } else {
            "FUS_QUICKFIX_NAME"
        }

        val quickFixName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $fusDirectiveName:")
        if (quickFixName.isNullOrEmpty()) {
            val expected = """
                |$fileText
                |// $fusDirectiveName: $calledId
            """.trimMargin()
            throw FileComparisonFailedError(
                "expected to find quickfix `called` id", fileText, expected, file.toString()
            )
        }

        if (calledId != quickFixName) {
            throw FileComparisonFailedError(
                "expected to find quickfix `called` $quickFixName",
                fileText,
                fileText.replace("// $fusDirectiveName: $quickFixName", "// $fusDirectiveName: $calledId"),
                file.toString()
            )
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor =
        if ("createfromusage" in testDataDirectory.path.toLowerCase()) {
            // TODO: WTF
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
        } else {
            super.getProjectDescriptor()
        }

    override val captureExceptions: Boolean
        get() = false

    private fun shouldBeAvailableAfterExecution(): Boolean = InTextDirectivesUtils.isDirectiveDefined(
        myFixture.file.text,
        "// $SHOULD_BE_AVAILABLE_AFTER_EXECUTION_DIRECTIVE"
    )

    protected open fun configExtra(options: String) {

    }

    private fun getPathAccordingToPackage(name: String, text: String): String {
        val packagePath = text.lines().let { list -> list.find { it.trim().startsWith("package") } }
            ?.removePrefix("package")
            ?.trim()?.replace(".", "/") ?: ""
        return "$packagePath/$name"
    }

    private fun doKotlinQuickFixTest(beforeFileName: String) {
        val testFile = File(beforeFileName)

        var fileText = ""
        var expectedErrorMessage: String? = ""
        var fixtureClasses = emptyList<String>()
        try {
            fileText = FileUtil.loadFile(testFile, CharsetToolkit.UTF8)
            TestCase.assertTrue("\"<caret>\" is missing in file \"${testFile.path}\"", fileText.contains("<caret>"))

            fixtureClasses = InTextDirectivesUtils.findListWithPrefixes(fileText, "// $FIXTURE_CLASS_DIRECTIVE: ")
            runInEdtAndWait {
                for (fixtureClass in fixtureClasses) {
                    TestFixtureExtension.loadFixture(fixtureClass, module)
                }
            }

            expectedErrorMessage = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $SHOULD_FAIL_WITH_DIRECTIVE: ")
            val contents = StringUtil.convertLineSeparators(fileText)
            var fileName = testFile.canonicalFile.name
            val putIntoPackageFolder =
                InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $FORCE_PACKAGE_FOLDER_DIRECTIVE") != null

            runInEdtAndWait {
                if (putIntoPackageFolder) {
                    fileName = getPathAccordingToPackage(fileName, contents)
                    myFixture.addFileToProject(fileName, contents)
                    myFixture.configureByFile(fileName)
                } else {
                    myFixture.configureByText(fileName, contents)
                }

                // The script configuration must not be loaded during highlighting. It should also be loaded as soon as possible, so we run
                // it together with the fixture's file configuration in the EDT.
                if (myFixture.file is KtFile) {
                    loadScriptConfiguration(myFixture.file as KtFile)
                }

                checkForUnexpectedActions()
            }

            configExtra(fileText)

            val hint = myFixture.file.actionHint(contents.replace("\${file}", fileName, ignoreCase = true))
            actionHint = hint
            val intention = runInEdtAndGet { findActionWithText(hint.expectedText) }
            if (hint.shouldPresent()) {
                if (intention == null) {
                    fail(
                        "Action with text '" + hint.expectedText + "' not found\n${myFixture.availableIntentions.size} available actions:\n" +
                                myFixture.availableIntentions.joinToString(separator = "\n") { "// \"${it.text}\" \"true\"" })
                    return
                }

                runReadAction {
                    if (intention.isAvailable(project, myFixture.editor, file)) {
                        IntentionManagerSettings.getInstance().isShowLightBulb(intention)
                    }
                }

                runInEdtAndWait {
                    CommandProcessor.getInstance().executeCommand(project, {
                        applyAction(contents, hint, intention, fileName)
                    }, "", "")
                }
            } else {
                assertNull("Action with text ${hint.expectedText} is present, but should not", intention)
            }

            val compilerArgumentsAfter = InTextDirectivesUtils.findStringWithPrefixes(fileText, "COMPILER_ARGUMENTS_AFTER: ")
            if (compilerArgumentsAfter != null) {
                val facetSettings = KotlinFacet.get(module)!!.configuration.settings
                val compilerSettings = facetSettings.compilerSettings
                TestCase.assertEquals(compilerArgumentsAfter, compilerSettings?.additionalArguments)
            }

            UsefulTestCase.assertEmpty(expectedErrorMessage)
        } catch (e: AssertionError) {
            throw e
        } catch (e: Throwable) {
            if (expectedErrorMessage == null) {
                throw e
            } else {
                Assert.assertEquals("Wrong exception message", expectedErrorMessage, e.message)
            }
        } finally {
            runInEdtAndWait {
                for (fixtureClass in fixtureClasses) {
                    TestFixtureExtension.unloadFixture(fixtureClass)
                }
                ConfigLibraryUtil.unconfigureLibrariesByDirective(myFixture.module, fileText)
            }
        }
    }

    private fun loadScriptConfiguration(file: KtFile) {
        ScriptConfigurationManager.getInstance(project).getConfiguration(file)
    }

    private fun PsiFile.actionHint(contents: String): ActionHint {
      return ActionHint.parse(this, contents,
                              actionPrefix?.let { ".*//(?: $it)?" } ?: "//",
                              true)
    }

    private fun applyAction(contents: String, hint: ActionHint, intention: IntentionAction, fileName: String) {
        val unwrappedIntention = unwrapIntention(intention)

        DirectiveBasedActionUtils.checkPriority(contents, unwrappedIntention)

        val writeActionResolveHandler: () -> Unit = {
            val intentionClassName = unwrappedIntention.javaClass.name
            if (!quickFixesAllowedToResolveInWriteAction.isWriteActionAllowed(intentionClassName)) {
                throw ResolveInDispatchThreadException("Resolve is not allowed under the write action for `$intentionClassName`!")
            }
        }

        val applyQuickFix = applyQuickFix()
        val stubComparisonFailure: ComparisonFailure?
        if (applyQuickFix) {
            val element = PsiUtilBase.getElementAtCaret(editor)
            stubComparisonFailure = try {
                forceCheckForResolveInDispatchThreadInTests(writeActionResolveHandler) {
                    myFixture.launchAction(intention)
                }
                null
            } catch (comparisonFailure: ComparisonFailure) {
                comparisonFailure
            }

            UIUtil.dispatchAllInvocationEvents()
            UIUtil.dispatchAllInvocationEvents()

            if (!shouldBeAvailableAfterExecution()) {
                var action = findActionWithText(hint.expectedText)
                action = if (action == null) null else IntentionActionDelegate.unwrap(action)
                if (action != null && !Comparing.equal(element, PsiUtilBase.getElementAtCaret(editor))) {
                    fail("Action '${hint.expectedText}' (${action.javaClass}) is still available after its invocation in test " + fileName)
                }
            }
        } else {
            stubComparisonFailure = null
        }

        myFixture.checkResultByFile(getAfterFileName(fileName))

        stubComparisonFailure?.let { throw it }
    }

    private fun applyQuickFix() = InTextDirectivesUtils.getPrefixedBoolean(myFixture.file.text, "$APPLY_QUICKFIX_DIRECTIVE:") != false

    open fun getAfterFileName(beforeFileName: String): String {
        return File(beforeFileName).name + ".after"
    }

    private fun checkForUnexpectedActions() {
        val text = myFixture.editor.document.text
        val actionHint = myFixture.file.actionHint(text)
        if (!InTextDirectivesUtils.isDirectiveDefined(text, DirectiveBasedActionUtils.ACTION_DIRECTIVE)) {
            return
        }

        myFixture.doHighlighting()
        val cachedIntentions = ShowIntentionActionsHandler.calcCachedIntentions(project, editor, file)
        cachedIntentions.wrapAndUpdateGutters()
        val actions = cachedIntentions.allActions.map { it.action }.toMutableList()

        val prefix = "class "
        if (actionHint.expectedText.startsWith(prefix)) {
            val className = actionHint.expectedText.substring(prefix.length)
            val aClass = Class.forName(className)
            assert(IntentionAction::class.java.isAssignableFrom(aClass) || ModCommandAction::class.java.isAssignableFrom(aClass)) {
                "$className should be inheritor of IntentionAction or ModCommandAction"
            }

            val validActions = HashSet(InTextDirectivesUtils.findLinesWithPrefixesRemoved(text, DirectiveBasedActionUtils.ACTION_DIRECTIVE))

            actions.removeAll { action -> !aClass.isAssignableFrom(action.javaClass) || validActions.contains(action.text) }

            if (actions.isNotEmpty()) {
                Assert.fail(
                    "Unexpected intention actions present\n " + actions.map { action ->
                        action.javaClass.toString() + " " + action.toString() + "\n"
                    }
                )
            }
        } else {
            // Action shouldn't be found. Check that other actions are expected and thus tested action isn't there under another name.
            checkAvailableActionsAreExpected(actions)
        }
    }

    private fun findActionWithText(text: String): IntentionAction? {
        val pattern = IntentionActionNamePattern(text)
        val intention = pattern.findActionByPattern(myFixture.availableIntentions, false)
        if (intention != null) return intention

        // Support warning suppression
        val caretOffset = myFixture.caretOffset
        val highlightInfos = myFixture.doHighlighting()
        val file = myFixture.file
        val editor = myFixture.editor
        //DaemonCodeAnalyzerImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(file, editor)
        for (highlight in highlightInfos) {
            if (highlight.startOffset <= caretOffset && caretOffset <= highlight.endOffset) {
                val group = highlight.problemGroup
                if (group is SuppressableProblemGroup) {
                    val at = file.findElementAt(highlight.actualStartOffset) ?: continue
                    val action = highlight.findRegisteredQuickFix<IntentionAction?> { desc, range ->
                        desc.getOptions(at, null).find { action ->
                            action.text == text && action.isAvailable(project, editor, file)
                        }
                    }
                    if (action != null) {
                        return action
                    }
                }
            }
        }

        return null
    }

    protected open fun checkAvailableActionsAreExpected(actions: List<IntentionAction>) {
        DirectiveBasedActionUtils.checkAvailableActionsAreExpected(dataFile(), actions)
    }

    protected open fun checkForUnexpectedErrors() = DirectiveBasedActionUtils.checkForUnexpectedErrors(myFixture.file as KtFile)

    override val additionalToolDirectives: Array<String>
        get() = arrayOf(if (isFirPlugin) K2_TOOL_DIRECTIVE else K1_TOOL_DIRECTIVE)

    protected open val actionPrefix: String? = null
}
