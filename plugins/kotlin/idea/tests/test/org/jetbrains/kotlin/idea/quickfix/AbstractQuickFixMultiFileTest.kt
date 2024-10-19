// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtil
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.quickfix.utils.findInspectionFile
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

abstract class AbstractQuickFixMultiFileTest : KotlinLightCodeInsightFixtureTestCase() {
    protected open fun doTestWithExtraFile(beforeFileName: String) {
        val disableTestDirective = if (isFirPlugin) IgnoreTests.DIRECTIVES.IGNORE_K2_MULTILINE_COMMENT else IgnoreTests.DIRECTIVES.IGNORE_K1
        IgnoreTests.runTestIfNotDisabledByFileDirective(Paths.get(beforeFileName), disableTestDirective) {
            enableInspections(beforeFileName)

            if (beforeFileName.endsWith(".test")) {
                doMultiFileTest(beforeFileName)
            } else {
                doTest(beforeFileName)
            }
        }
    }

    private fun enableInspections(beforeFileName: String) {
        val inspectionFile = findInspectionFile(File(beforeFileName).parentFile, this.pluginMode)
        if (inspectionFile != null) {
            val className = FileUtil.loadFile(inspectionFile).trim { it <= ' ' }
            val inspectionClass = Class.forName(className)
            enableInspectionTools(inspectionClass)
        }
    }

    private fun enableInspectionTools(klass: Class<*>) {
        val eps = mutableListOf<InspectionEP>().apply {
            addAll(LocalInspectionEP.LOCAL_INSPECTION.extensions)
            addAll(InspectionEP.GLOBAL_INSPECTION.extensions)
        }

        val tool = eps.firstOrNull { it.implementationClass == klass.name }?.instantiateTool()
            ?: error("Could not find inspection tool for class: $klass")

        myFixture.enableInspections(tool)
    }

    override fun setUp() {
        super.setUp()
        CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = arrayOf("excludedPackage", "somePackage.ExcludedClass")
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = ArrayUtil.EMPTY_STRING_ARRAY },
            ThrowableRunnable { super.tearDown() }
        )
    }

    /**
     * @param subFiles   subFiles of multiFile test
     * *
     * @param beforeFile will be added last, as subFiles are dependencies of it
     */
    private fun configureMultiFileTest(subFiles: List<TestFile>, beforeFile: TestFile): List<VirtualFile> {
        val vFiles = subFiles.map(this::createTestFile).toMutableList()
        val beforeVFile = createTestFile(beforeFile)
        vFiles.add(beforeVFile)
        myFixture.configureFromExistingVirtualFile(beforeVFile)
        TestCase.assertEquals(guessFileType(beforeFile), myFixture.file.virtualFile.fileType)

        TestCase.assertTrue("\"<caret>\" is probably missing in file \"" + beforeFile.path + "\"", myFixture.editor.caretModel.offset != 0)
        return vFiles
    }

    private fun createTestFile(testFile: TestFile): VirtualFile {
        return runWriteAction {
            val vFile = myFixture.tempDirFixture.createFile(testFile.path)
            vFile.charset = StandardCharsets.UTF_8
            VfsUtil.saveText(vFile, testFile.content)
            vFile
        }
    }

    private fun doMultiFileTest(beforeFileName: String) {
        val mainFile = File(beforeFileName)
        val multiFileText = FileUtil.loadFile(mainFile, true)

        val subFiles = TestFiles.createTestFiles(
            "single.kt",
            multiFileText,
            object : TestFiles.TestFileFactoryNoModules<TestFile>() {
                override fun create(fileName: String, text: String, directives: Directives): TestFile {
                    val linesWithoutDirectives = text.lines().filter {
                        !it.startsWith("// LANGUAGE_VERSION") && !it.startsWith("// FILE")
                    }
                    return TestFile(fileName, linesWithoutDirectives.joinToString(separator = "\n"))
                }
            }
        )

        fun firstFileWith(subStringInName: String) = subFiles.firstOrNull { file -> file.path.contains(subStringInName) }

        val afterFile = if (isFirPlugin) {
            firstFileWith(".after.fir") ?: firstFileWith(".after")
        } else {
            firstFileWith(".after")
        }
        val beforeFile = firstFileWith(".before")!!

        subFiles.remove(beforeFile)
        if (afterFile != null) {
            subFiles.remove(afterFile)
        }

        configureMultiFileTest(subFiles, beforeFile)
        withCustomCompilerOptions(multiFileText, project, module) {
            project.executeCommand("") {
                try {
                    val psiFile = file

                    val actionHint = ActionHint.parse(psiFile, beforeFile.content)
                    val text = actionHint.expectedText

                    val actionShouldBeAvailable = actionHint.shouldPresent()

                    if (psiFile is KtFile) {
                        checkForUnexpectedErrors(psiFile)
                    }

                    doAction(
                        mainFile,
                        text,
                        file,
                        editor,
                        actionShouldBeAvailable,
                        getTestName(false),
                        this::availableActions,
                        myFixture::doHighlighting,
                        checkAvailableActionsAreExpected = this::checkAvailableActionsAreExpected
                    )

                    val actualText = file.text
                    val afterText = StringBuilder(actualText).insert(editor.caretModel.offset, "<caret>").toString()

                    if (actionShouldBeAvailable) {
                        TestCase.assertNotNull(".after file should exist", afterFile)
                        if (afterText != afterFile!!.content) {
                            val actualTestFile = StringBuilder()
                            if (multiFileText.startsWith("// LANGUAGE_VERSION")) {
                                actualTestFile.append(multiFileText.lineSequence().first())
                            }

                            actualTestFile.append("// FILE: ").append(beforeFile.path).append("\n").append(beforeFile.content)
                            for (file in subFiles) {
                                actualTestFile.append("// FILE: ").append(file.path).append("\n").append(file.content)
                            }
                            actualTestFile.append("// FILE: ").append(afterFile.path).append("\n").append(afterText)

                            KotlinTestUtils.assertEqualsToFile(mainFile, actualTestFile.toString())
                        }
                    } else {
                        TestCase.assertNull(".after file should not exist", afterFile)
                    }
                } catch (e: AssertionError) {
                    throw e
                } catch (e: Throwable) {
                    e.printStackTrace()
                    TestCase.fail(getTestName(true))
                }
            }
        }

    }

    private fun doTest(beforeFilePath: String) {
        val mainFile = File(beforeFilePath)
        val originalFileText = FileUtil.loadFile(mainFile, true)
        val mainFileDir = mainFile.parentFile!!

        val mainFileName = mainFile.name
        val extraFiles = mainFileDir.listFiles { _, name ->
            name.startsWith(extraFileNamePrefix(mainFileName))
                    && name != mainFileName
                    && PathUtil.getFileExtension(name).let { it == "kt" || it == "java" || it == "groovy" }
        }!!

        val testFiles = ArrayList<String>()
        testFiles.add(mainFile.name)
        extraFiles.mapTo(testFiles) { file -> file.name }

        myFixture.configureByFiles(*testFiles.toTypedArray())

        withCustomCompilerOptions(originalFileText, project, module) {
            project.executeCommand("") {
                try {
                    val psiFile = file

                    val actionHint = ActionHint.parse(psiFile, originalFileText)
                    val text = actionHint.expectedText

                    val actionShouldBeAvailable = actionHint.shouldPresent()

                    if (psiFile is KtFile) {
                        checkForUnexpectedErrors(psiFile)
                    }

                    doAction(
                        mainFile,
                        text,
                        file,
                        editor,
                        actionShouldBeAvailable,
                        beforeFilePath,
                        this::availableActions,
                        myFixture::doHighlighting,
                        checkAvailableActionsAreExpected = this::checkAvailableActionsAreExpected
                    )

                    if (actionShouldBeAvailable) {
                        val afterMain = findAfterFile(mainFile.path)
                        try {
                            myFixture.checkResultByFile(afterMain.name)
                        } catch (e: FileComparisonFailedError) {
                            KotlinTestUtils.assertEqualsToFile(afterMain, editor)
                        }

                        for (file in myFixture.file.containingDirectory.files) {
                            val fileName = file.name
                            if (fileName == myFixture.file.name || !fileName.startsWith(extraFileNamePrefix(myFixture.file.name))) continue

                            val extraFileFullPath = beforeFilePath.replace(myFixture.file.name, fileName)
                            val afterFile = findAfterFile(extraFileFullPath)
                            KotlinTestUtils.assertEqualsToFile(afterFile, file.text)
                        }
                    }
                } catch (e: AssertionError) {
                    throw e
                } catch (e: Throwable) {
                    e.printStackTrace()
                    TestCase.fail(getTestName(true))
                }
            }
        }
    }

    private fun findAfterFile(fullPath: String): File {
        val path = fullPath.replace(".before.Main.", ".before.")
        val afterTokens = (if (pluginMode == KotlinPluginMode.K2) arrayOf(".after.k2.") else arrayOf()) + ".after."
        for (afterToken in afterTokens) {
            val file = File(path.replace(".before.", afterToken))
            if (file.exists()) return file
        }
        return File(path)
    }

    protected open fun checkForUnexpectedErrors(file: KtFile) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(file)
    }

    protected open fun checkAvailableActionsAreExpected(file: File, actions: Collection<IntentionAction>) {
        DirectiveBasedActionUtils.checkAvailableActionsAreExpected(file, availableActions)
    }

    private val availableActions: List<IntentionAction>
        get() {
            myFixture.doHighlighting()
            val cachedIntentions = ShowIntentionActionsHandler.calcCachedIntentions(project, editor, file)
            cachedIntentions.wrapAndUpdateGutters()
            return cachedIntentions.allActions.map { it.action }
        }

    class TestFile internal constructor(val path: String, val content: String)

    companion object {
        private fun getActionsTexts(availableActions: List<IntentionAction>): List<String> =
            availableActions.map(IntentionAction::getText)

        private fun extraFileNamePrefix(mainFileName: String): String =
            mainFileName.replace(".Main.kt", ".").replace(".Main.java", ".")

        protected fun guessFileType(file: TestFile): FileType = when {
            file.path.contains("." + KotlinFileType.EXTENSION) -> KotlinFileType.INSTANCE
            file.path.contains("." + JavaFileType.DEFAULT_EXTENSION) -> JavaFileType.INSTANCE
            else -> PlainTextFileType.INSTANCE
        }

        fun doAction(
            mainFile: File,
            text: String,
            file: PsiFile,
            editor: Editor,
            actionShouldBeAvailable: Boolean,
            testFilePath: String,
            getAvailableActions: () -> List<IntentionAction>,
            doHighlighting: () -> List<HighlightInfo>,
            shouldBeAvailableAfterExecution: Boolean = false,
            checkAvailableActionsAreExpected: (File, Collection<IntentionAction>) -> Unit =
                DirectiveBasedActionUtils::checkAvailableActionsAreExpected
        ) {
            val pattern = IntentionActionNamePattern(text)

            val availableActions = getAvailableActions()
            val action = pattern.findActionByPattern(availableActions, acceptMatchByFamilyName = !actionShouldBeAvailable)

            if (action == null) {
                if (actionShouldBeAvailable) {
                    val texts = getActionsTexts(availableActions.filter {
                        ShowIntentionActionsHandler.availableFor(file, editor, editor.caretModel.offset, it)
                    })
                    val infos = doHighlighting()
                    TestCase.fail(
                        "Action with text '" + text + "' is not available in test " + testFilePath + "\n" +
                                "Available actions (" + texts.size + "): \n" +
                                StringUtil.join(texts, "\n") +
                                "\nActions:\n" +
                                StringUtil.join(availableActions, "\n") +
                                "\nInfos:\n" +
                                StringUtil.join(infos, "\n")
                    )
                } else {
                    checkAvailableActionsAreExpected(mainFile, availableActions)
                }
            } else {
                if (!actionShouldBeAvailable) {
                    TestCase.fail("Action '$text' is available (but must not) in test $testFilePath")
                }

                CodeInsightTestFixtureImpl.invokeIntention(action, file, editor)

                if (!shouldBeAvailableAfterExecution) {
                    val afterAction = pattern.findActionByPattern(getAvailableActions(), acceptMatchByFamilyName = true)

                    if (afterAction != null) {
                        TestCase.fail("Action '$text' is still available after its invocation in test $testFilePath")
                    }
                }
            }
        }
    }
}
