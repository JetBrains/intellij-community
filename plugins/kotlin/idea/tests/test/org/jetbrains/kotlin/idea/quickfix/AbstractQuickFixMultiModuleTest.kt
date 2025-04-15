// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest.Companion.K1_TOOL_DIRECTIVE
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest.Companion.K2_TOOL_DIRECTIVE
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File
import java.nio.file.Paths

abstract class AbstractQuickFixMultiModuleTest : AbstractMultiModuleTest(), QuickFixTest {
    protected fun dataFile(fileName: String): File = File(testDataPath, fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected open fun fileName(): String = KotlinTestUtils.getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    override fun getTestDataDirectory(): File {
        return File(TestMetadataUtil.getTestDataPath(this::class.java))
    }

    override val additionalToolDirectives: Array<String>
        get() {
            val directive = when (pluginMode) {
                KotlinPluginMode.K1 -> K1_TOOL_DIRECTIVE
                KotlinPluginMode.K2 -> K2_TOOL_DIRECTIVE
            }
            return arrayOf(directive)
        }

    fun doTest(unused: String) {
        setupMppProjectFromDirStructure(dataFile())
        val actionFile = project.findFileWithCaret()
        val virtualFilePath = actionFile.virtualFile!!.toNioPath()

        val ignoreDirective = IgnoreTests.DIRECTIVES.of(pluginMode)

        IgnoreTests.runTestIfNotDisabledByFileDirective(virtualFilePath, ignoreDirective) {
            val directiveFileText = actionFile.text
            withCustomCompilerOptions(directiveFileText, project, module) {
                doQuickFixTest(fileName())
            }
        }
    }

    protected open val actionPrefix: String? = null

    private fun doQuickFixTest(dirPath: String) {
        KotlinTestHelpers.registerChooserInterceptor(testRootDisposable)

        val actionFile = project.findFileWithCaret()
        val virtualFile = actionFile.virtualFile!!
        val mainFile = virtualFile.toIOFile()?.takeIf(File::exists) ?: error("unable to lookup source io file")
        configureByExistingFile(virtualFile)
        val actionFileText = actionFile.text
        val actionFileName = actionFile.name
        val inspections = parseInspectionsToEnable(virtualFile.path, actionFileText).toTypedArray()
        enableInspectionTools(*inspections)

        project.executeCommand("") {
            var expectedErrorMessage = ""
            try {
                val actionHint = ActionHint.parse(actionFile, actionFileText, actionPrefix?.let { ".*//(?: $it)?" } ?: "//", true)
                val text = actionHint.expectedText

                val actionShouldBeAvailable = actionHint.shouldPresent()

                expectedErrorMessage = InTextDirectivesUtils.findListWithPrefixes(
                    actionFileText,
                    "// SHOULD_FAIL_WITH: "
                ).joinToString(separator = "\n")

                TypeAccessibilityChecker.testLog = StringBuilder()
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
                        shouldBeAvailableAfterExecution = InTextDirectivesUtils.isDirectiveDefined(actionFile.text, "// SHOULD_BE_AVAILABLE_AFTER_EXECUTION")
                    )

                    TypeAccessibilityChecker.testLog.toString()
                } finally {
                    TypeAccessibilityChecker.testLog = null
                }

                if (actionFile is KtFile) {
                    when (pluginMode) {
                        KotlinPluginMode.K1 -> DirectiveBasedActionUtils.checkForUnexpectedErrors(actionFile)
                        KotlinPluginMode.K2 -> {} // TODO check diagnostics for K2
                    }
                }

                NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

                if (actionShouldBeAvailable) {
                    compareToExpected(dirPath)
                }

                UsefulTestCase.assertEmpty(expectedErrorMessage)
                val logFile = Paths.get(testDataPath, dirPath, "log.log").toFile()
                if (log.isNotEmpty()) {
                    KotlinTestUtils.assertEqualsToFile(logFile, log)
                } else {
                    TestCase.assertFalse(logFile.exists())
                }

            } catch (e: AssertionError) {
                throw e
            } catch (e: Throwable) {
                if (expectedErrorMessage.isEmpty()) {
                    e.printStackTrace()
                    TestCase.fail(getTestName(true))
                } else {
                    Assert.assertEquals("Wrong exception message", expectedErrorMessage, e.message)
                    compareToExpected(dirPath)
                }
            }
        }
    }

    private fun compareToExpected(directory: String) {
        val projectDirectory = File(testDataPath, directory)
        val afterFiles = projectDirectory.walkTopDown().filter { it.path.endsWith(".after") }.toList()

        for (editedFile in project.allKotlinFiles()) {
            val afterFileInTmpProject = findAfterFile(editedFile) ?: continue
            val afterFileInTestData = afterFiles.filter { it.name == afterFileInTmpProject.name }.single {
                it.readText() == File(afterFileInTmpProject.virtualFile.path).readText()
            }

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

    override fun getTestProjectJdk() = IdeaTestUtil.getMockJdk18()
}
