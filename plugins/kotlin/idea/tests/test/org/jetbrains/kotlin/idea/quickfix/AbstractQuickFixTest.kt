// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.caches.resolve.ResolveInDispatchThreadException
import org.jetbrains.kotlin.idea.caches.resolve.forceCheckForResolveInDispatchThreadInTests
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import org.junit.ComparisonFailure
import java.io.File

abstract class AbstractQuickFixTest : KotlinLightCodeInsightFixtureTestCase(), QuickFixTest {
    companion object {
        const val APPLY_QUICKFIX_DIRECTIVE = "APPLY_QUICKFIX"
        const val ACTION_DIRECTIVE = "ACTION"
        const val SHOULD_BE_AVAILABLE_AFTER_EXECUTION_DIRECTIVE = "SHOULD_BE_AVAILABLE_AFTER_EXECUTION"
        const val FIXTURE_CLASS_DIRECTIVE = "FIXTURE_CLASS"
        const val SHOULD_FAIL_WITH_DIRECTIVE = "SHOULD_FAIL_WITH"
        const val FORCE_PACKAGE_FOLDER_DIRECTIVE = "FORCE_PACKAGE_FOLDER"
        const val PRIORITY_DIRECTIVE = "PRIORITY"
        const val K1_TOOL_DIRECTIVE = "K1_TOOL:"
        const val K2_TOOL_DIRECTIVE = "K2_TOOL:"

        private val quickFixesAllowedToResolveInWriteAction = AllowedToResolveUnderWriteActionData(
            IDEA_TEST_DATA_DIR.resolve("quickfix/allowResolveInWriteAction.txt").path,
            """
                    # Actions that are allowed to resolve in write action. Normally this list shouldn't be extended and eventually should
                    # be dropped. Please consider rewriting a quick-fix and remove resolve from it before adding a new entry to this list.
            """.trimIndent(),
        )

        private fun unwrapIntention(action: Any): Any = when (action) {
            is IntentionActionDelegate -> unwrapIntention(action.delegate)
            is QuickFixWrapper -> unwrapIntention(action.fix)
            else -> action
        }
    }

    protected open fun doTest(beforeFileName: String) {
        val beforeFileText = FileUtil.loadFile(File(beforeFileName))
        InTextDirectivesUtils.checkIfMuted(beforeFileText)
        withCustomCompilerOptions(beforeFileText, project, module) {
            val inspections = parseInspectionsToEnable(beforeFileName, beforeFileText).toTypedArray()
            try {
                myFixture.enableInspections(*inspections)

                doKotlinQuickFixTest(beforeFileName)
                checkForUnexpectedErrors()
                PsiTestUtil.checkPsiStructureWithCommit(file, PsiTestUtil::checkPsiMatchesTextIgnoringNonCode)
            } finally {
                myFixture.disableInspections(*inspections)
            }
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
        CommandProcessor.getInstance().executeCommand(project, {
            var fileText = ""
            var expectedErrorMessage: String? = ""
            var fixtureClasses = emptyList<String>()
            try {
                fileText = FileUtil.loadFile(testFile, CharsetToolkit.UTF8)
                TestCase.assertTrue("\"<caret>\" is missing in file \"${testFile.path}\"", fileText.contains("<caret>"))

                fixtureClasses = InTextDirectivesUtils.findListWithPrefixes(fileText, "// $FIXTURE_CLASS_DIRECTIVE: ")
                for (fixtureClass in fixtureClasses) {
                    TestFixtureExtension.loadFixture(fixtureClass, module)
                }

                expectedErrorMessage = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $SHOULD_FAIL_WITH_DIRECTIVE: ")
                val contents = StringUtil.convertLineSeparators(fileText)
                var fileName = testFile.canonicalFile.name
                val putIntoPackageFolder =
                    InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $FORCE_PACKAGE_FOLDER_DIRECTIVE") != null
                if (putIntoPackageFolder) {
                    fileName = getPathAccordingToPackage(fileName, contents)
                    myFixture.addFileToProject(fileName, contents)
                    myFixture.configureByFile(fileName)
                } else {
                    myFixture.configureByText(fileName, contents)
                }

                checkForUnexpectedActions()

                configExtra(fileText)

                applyAction(contents, fileName)

                val compilerArgumentsAfter = InTextDirectivesUtils.findStringWithPrefixes(fileText, "COMPILER_ARGUMENTS_AFTER: ")
                if (compilerArgumentsAfter != null) {
                    val facetSettings = KotlinFacet.get(module)!!.configuration.settings
                    val compilerSettings = facetSettings.compilerSettings
                    TestCase.assertEquals(compilerArgumentsAfter, compilerSettings?.additionalArguments)
                }

                UsefulTestCase.assertEmpty(expectedErrorMessage)
            } catch (e: FileComparisonFailure) {
                throw e
            } catch (e: AssertionError) {
                throw e
            } catch (e: Throwable) {
                if (expectedErrorMessage == null) {
                    throw e
                } else {
                    Assert.assertEquals("Wrong exception message", expectedErrorMessage, e.message)
                }
            } finally {
                for (fixtureClass in fixtureClasses) {
                    TestFixtureExtension.unloadFixture(fixtureClass)
                }
                ConfigLibraryUtil.unconfigureLibrariesByDirective(myFixture.module, fileText)
            }
        }, "", "")
    }

    private fun applyAction(contents: String, fileName: String) {
        val actionHint = ActionHint.parse(myFixture.file, contents.replace("\${file}", fileName, ignoreCase = true))
        val intention = findActionWithText(actionHint.expectedText)
        if (actionHint.shouldPresent()) {
            if (intention == null) {
                fail(
                    "Action with text '" + actionHint.expectedText + "' not found\nAvailable actions:\n" +
                            myFixture.availableIntentions.joinToString(separator = "\n") { "// \"${it.text}\" \"true\"" })
                return
            }

            val unwrappedIntention = unwrapIntention(intention)
            if (shouldCheckIntentionActionType) {
                assertInstanceOf(unwrappedIntention, QuickFixActionBase::class.java)
            }
            val priorityName = InTextDirectivesUtils.findStringWithPrefixes(contents, "// $PRIORITY_DIRECTIVE: ")
            if (priorityName != null) {
                val expectedPriority = enumValueOf<PriorityAction.Priority>(priorityName)
                val actualPriority = (unwrappedIntention as? PriorityAction)?.priority
                assertTrue(
                    "Expected action priority: $expectedPriority\nActual priority: $actualPriority",
                    expectedPriority == actualPriority
                )
            }

            val writeActionResolveHandler: () -> Unit = {
                val intentionClassName = unwrappedIntention.javaClass.name
                if (!quickFixesAllowedToResolveInWriteAction.isWriteActionAllowed(intentionClassName)) {
                    throw ResolveInDispatchThreadException("Resolve is not allowed under the write action for `$intentionClassName`!")
                }
            }

            val applyQuickFix = (InTextDirectivesUtils.findStringWithPrefixes(myFixture.file.text, "// $APPLY_QUICKFIX_DIRECTIVE: ")
                ?: "true").toBoolean()
            val stubComparisonFailure: ComparisonFailure?
            if (applyQuickFix) {
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
                    var action = findActionWithText(actionHint.expectedText)
                    action = if (action == null) null else IntentionActionDelegate.unwrap(action)
                    assertNull(
                        "Action '${actionHint.expectedText}' (${action?.javaClass}) is still available after its invocation in test " + fileName,
                        action
                    )
                }
            } else {
                stubComparisonFailure = null
            }

            myFixture.checkResultByFile(getAfterFileName(fileName))

            stubComparisonFailure?.let { throw it }
        } else {
            assertNull("Action with text ${actionHint.expectedText} is present, but should not", intention)
        }
    }

    protected open fun getAfterFileName(beforeFileName: String): String {
        return File(beforeFileName).name + ".after"
    }

    /**
     * If true, the type of the [IntentionAction] to invoke is [QuickFixActionBase]. This ensures that the action is coming from a
     * quickfix (i.e., diagnostic-based), and not a regular IDE intention.
     */
    protected open val shouldCheckIntentionActionType: Boolean
        // For FE 1.0, many quickfixes are implemented as IntentionActions, which may or may not be used as regular IDE intentions as well
        get() = false

    private fun checkForUnexpectedActions() {
        val text = myFixture.editor.document.text
        val actionHint = ActionHint.parse(myFixture.file, text)
        if (actionHint.shouldPresent() && !InTextDirectivesUtils.isDirectiveDefined(text, DirectiveBasedActionUtils.ACTION_DIRECTIVE)) {
            return
        }

        myFixture.doHighlighting()
        val intentions = ShowIntentionActionsHandler.calcIntentions(project, editor, file)
        val cachedIntentions = CachedIntentions.create(project, file, editor, intentions)
        cachedIntentions.wrapAndUpdateGutters()
        val actions = cachedIntentions.allActions.map { it.action }.toMutableList()

        val prefix = "class "
        if (actionHint.expectedText.startsWith(prefix)) {
            val className = actionHint.expectedText.substring(prefix.length)
            val aClass = Class.forName(className)
            assert(IntentionAction::class.java.isAssignableFrom(aClass)) { "$className should be inheritor of IntentionAction" }

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
        val intentions = myFixture.availableIntentions.filter { it.text == text }
        if (intentions.isNotEmpty()) return intentions.first()

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
}
