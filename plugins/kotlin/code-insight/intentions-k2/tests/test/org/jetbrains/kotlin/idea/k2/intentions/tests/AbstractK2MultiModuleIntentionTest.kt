// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.intentions.tests

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.fir.K2DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiFileTest
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.idea.test.findFileWithCaret
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File
import java.nio.file.Paths

/**
 * This test was written based on [org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiModuleTest].
 */
abstract class AbstractK2MultiModuleIntentionTest : AbstractMultiModuleTest() {

    protected fun dataFile(fileName: String): File = File(/* parent = */ testDataPath, /* child = */ fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected fun fileName(): String =
        KotlinTestUtils.getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    override fun getTestDataDirectory(): File {
        return File(TestMetadataUtil.getTestDataPath(this::class.java))
    }

    @Suppress("unused")
    fun doTest(unused: String) {
        setupMppProjectFromDirStructure(dataFile())

        val actionFile = project.findFileWithCaret()
        val virtualFilePath = actionFile.virtualFile!!.toNioPath()
        val ignoreDirective = IgnoreTests.DIRECTIVES.of(pluginMode)

        IgnoreTests.runTestIfNotDisabledByFileDirective(virtualFilePath, ignoreDirective) {
            val directiveFileText = actionFile.text
            withCustomCompilerOptions(directiveFileText, project, module) {
                doIntentionTest(fileName())
            }
        }
    }

    protected val actionPrefix: String = "K2_ACTION:"

    private fun doIntentionTest(dirPath: String) {
        KotlinTestHelpers.registerChooserInterceptor(testRootDisposable)

        val actionFile = project.findFileWithCaret()
        val virtualFile = actionFile.virtualFile!!
        val mainFile = virtualFile.toIOFile()?.takeIf(File::exists) ?: error("unable to lookup source io file")
        configureByExistingFile(virtualFile)
        val actionFileText = actionFile.text
        val actionFileName = actionFile.name

        if (actionFile is KtFile) {
            K2DirectiveBasedActionUtils.checkForErrorsBefore(mainFile, actionFile, mainFile.readText())
        }

        project.executeCommand("") {
            var expectedErrorMessage = ""
            try {
                val actionHint = ActionHint.parse(actionFile, actionFileText, actionPrefix.let { ".*//(?: $it)?" }, true)
                val text = actionHint.expectedText

                val actionShouldBeAvailable = actionHint.shouldPresent()

                expectedErrorMessage = InTextDirectivesUtils.findListWithPrefixes(
                    actionFileText,
                    "// SHOULD_FAIL_WITH: "
                ).joinToString(separator = "\n")

                ExpectActualUtils.testLog = StringBuilder()
                val log = try {

                    AbstractQuickFixMultiFileTest.doAction(
                        mainFile,
                        text,
                        file,
                        editor,
                        actionShouldBeAvailable,
                        actionFileName,
                        actionHint,
                        this::availableActions,
                        this::doHighlighting,
                        pluginMode = pluginMode,
                        shouldBeAvailableAfterExecution = InTextDirectivesUtils.isDirectiveDefined(
                            actionFile.text,
                            "// SHOULD_BE_AVAILABLE_AFTER_EXECUTION"
                        )
                    )

                    ExpectActualUtils.testLog.toString()
                } finally {
                    ExpectActualUtils.testLog = null
                }

                NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

                if (actionShouldBeAvailable) {
                    compareToExpected(dirPath, mainFile)
                }

                assertEmpty(expectedErrorMessage)
                val logFile = Paths.get(testDataPath, dirPath, "log.log").toFile()
                if (log.isNotEmpty()) {
                    KotlinTestUtils.assertEqualsToFile(logFile, log)
                } else {
                    assertFalse(logFile.exists())
                }

            } catch (e: AssertionError) {
                throw e
            } catch (e: Throwable) {
                if (expectedErrorMessage.isEmpty()) {
                    e.printStackTrace()
                    fail(getTestName(/* lowercaseFirstLetter = */ true))
                } else {
                    Assert.assertEquals("Wrong exception message", expectedErrorMessage, e.message)
                    compareToExpected(dirPath, mainFile)
                }
            }
        }
    }

    private fun compareToExpected(directory: String, mainFile: File) {
        val projectDirectory = File(/* parent = */ testDataPath, /* child = */ directory)
        val afterFiles = projectDirectory.walkTopDown().filter { it.path.endsWith(".after") }.toList()

        for (editedFile in project.allKotlinFiles()) {
            val afterFileInTmpProject = findAfterFile(editedFile) ?: continue
            val afterFileInTestData = afterFiles.filter { it.name == afterFileInTmpProject.name }.single {
                it.readText() == File(afterFileInTmpProject.virtualFile.path).readText()
            }
            K2DirectiveBasedActionUtils.checkForErrorsAfter(mainFile, editedFile, mainFile.readText())

            setActiveEditor(editedFile.findExistingEditor() ?: createEditor(editedFile.virtualFile))
            try {
                checkResultByFile(afterFileInTestData.relativeTo(File(testDataPath)).path)
            } catch (_: FileComparisonFailedError) {
                KotlinTestUtils.assertEqualsToFile(afterFileInTestData, editor)
            }
        }
    }

    protected open fun findAfterFile(editedFile: KtFile): PsiFile? = editedFile.containingDirectory?.findFile(editedFile.name + ".after")

    private val availableActions: List<IntentionAction>
        get() {
            doHighlighting()
            return LightQuickFixTestCase.getAvailableActions(editor, file)
        }
}
