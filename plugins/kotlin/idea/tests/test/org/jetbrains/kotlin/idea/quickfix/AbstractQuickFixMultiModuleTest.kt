// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.ComparisonFailure
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
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

    fun doTest(unused: String) {
        setupMppProjectFromDirStructure(dataFile())
        val directiveFileText = project.findFileWithCaret().text
        withCustomCompilerOptions(directiveFileText, project, module) {
            doQuickFixTest(fileName())
        }
    }

    private fun VirtualFile.toIOFile(): File? {
        val paths = mutableListOf<String>()
        var vFile: VirtualFile? = this
        while (vFile != null) {
            vFile.sourceIOFile()?.let {
                return File(it, paths.reversed().joinToString("/"))
            }
            paths.add(vFile.name)
            vFile = vFile.parent
        }
        return null
    }

    private fun doQuickFixTest(dirPath: String) {
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
                val actionHint = ActionHint.parse(actionFile, actionFileText)
                val text = actionHint.expectedText

                val actionShouldBeAvailable = actionHint.shouldPresent()

                expectedErrorMessage = InTextDirectivesUtils.findListWithPrefixes(
                    actionFileText,
                    "// SHOULD_FAIL_WITH: "
                ).joinToString(separator = "\n")

                val dialogOption = when (InTextDirectivesUtils.findStringWithPrefixes(actionFileText, "// DIALOG_OPTION: ")) {
                    "OK" -> TestDialog.OK
                    "NO" -> TestDialog.NO
                    "CANCEL" -> TestDialog { Messages.CANCEL }
                    else -> TestDialog.DEFAULT
                }

                val oldDialogOption = TestDialogManager.setTestDialog(dialogOption)

                TypeAccessibilityChecker.testLog = StringBuilder()
                val log = try {

                    AbstractQuickFixMultiFileTest.doAction(
                        mainFile,
                        text,
                        file,
                        editor,
                        actionShouldBeAvailable,
                        actionFileName,
                        this::availableActions,
                        this::doHighlighting,
                        InTextDirectivesUtils.isDirectiveDefined(actionFile.text, "// SHOULD_BE_AVAILABLE_AFTER_EXECUTION")
                    )

                    TypeAccessibilityChecker.testLog.toString()
                } finally {
                    TestDialogManager.setTestDialog(oldDialogOption)
                    TypeAccessibilityChecker.testLog = null
                }

                if (actionFile is KtFile) {
                    DirectiveBasedActionUtils.checkForUnexpectedErrors(actionFile)
                }

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

            } catch (e: ComparisonFailure) {
                throw e
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
            val afterFileInTmpProject = editedFile.containingDirectory?.findFile(editedFile.name + ".after") ?: continue
            val afterFileInTestData = afterFiles.filter { it.name == afterFileInTmpProject.name }.single {
                it.readText() == File(afterFileInTmpProject.virtualFile.path).readText()
            }

            setActiveEditor(editedFile.findExistingEditor() ?: createEditor(editedFile.virtualFile))
            try {
                checkResultByFile(afterFileInTestData.relativeTo(File(testDataPath)).path)
            } catch (e: ComparisonFailure) {
                KotlinTestUtils.assertEqualsToFile(afterFileInTestData, editor)
            }
        }
    }

    private val availableActions: List<IntentionAction>
        get() {
            doHighlighting()
            return LightQuickFixTestCase.getAvailableActions(editor, file)
        }

    override fun getTestProjectJdk() = IdeaTestUtil.getMockJdk18()
}
