// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.PsiTestUtil
import junit.framework.ComparisonFailure
import junit.framework.TestCase
import org.jetbrains.kotlin.formatter.FormatSettingsUtil
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.junit.Assert
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class AbstractIntentionTest : KotlinLightCodeInsightFixtureTestCase() {
    protected open fun intentionFileName(): String = ".intention"

    protected open fun afterFileNameSuffix(ktFilePath: File): String = ".after"

    protected open fun isApplicableDirectiveName(): String = "IS_APPLICABLE"

    protected open fun isApplicableDirective(fileText: String): Boolean {
        val isApplicableString = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// ${isApplicableDirectiveName()}: ")
        return isApplicableString == null || isApplicableString == "true"
    }

    protected open fun intentionTextDirectiveName(): String = "INTENTION_TEXT"

    private fun createIntention(testDataFile: File): IntentionAction {
        val candidateFiles = mutableListOf<File>()

        var current: File? = testDataFile.parentFile
        while (current != null) {
            val candidate = File(current, intentionFileName())
            if (candidate.exists()) {
                candidateFiles.add(candidate)
            }
            current = current.parentFile
        }

        when (candidateFiles.size) {
            0 -> throw AssertionError(
                ".intention file is not found for " + testDataFile +
                        "\nAdd it to base directory of test data. It should contain fully-qualified name of intention class."
            )
            1 -> {
                val className = FileUtil.loadFile(candidateFiles[0]).trim { it <= ' ' }
                return Class.forName(className).getDeclaredConstructor().newInstance() as IntentionAction
            }
            else -> throw AssertionError(
                "Several .intention files are available for $testDataFile\nPlease remove some of them\n$candidateFiles"
            )
        }
    }

    @Throws(Exception::class)
    protected fun doTest(path: String) {
        val mainFile = File(path)
        val mainFileName = FileUtil.getNameWithoutExtension(mainFile)
        val intentionAction = createIntention(mainFile)
        val sourceFilePaths = ArrayList<String>()
        val parentDir = mainFile.parentFile
        var i = 1
        sourceFilePaths.add(mainFile.name)
        extraFileLoop@ while (true) {
            for (extension in EXTENSIONS) {
                val extraFile = File(parentDir, "$mainFileName.$i$extension")
                if (extraFile.exists()) {
                    sourceFilePaths.add(extraFile.name)
                    i++
                    continue@extraFileLoop
                }
            }
            break
        }

        val psiFiles = myFixture.configureByFiles(*sourceFilePaths.toTypedArray())
        val pathToFiles = mapOf(*(sourceFilePaths zip psiFiles).toTypedArray())

        val fileText = FileUtil.loadFile(mainFile, true)
        withCustomCompilerOptions(fileText, project, module) {
            ConfigLibraryUtil.configureLibrariesByDirective(module, fileText)
            configureCodeStyleAndRun(project, { FormatSettingsUtil.createConfigurator(fileText, it).configureSettings() }) {
                configureRegistryAndRun(fileText) {
                    try {
                        TestCase.assertTrue("\"<caret>\" is missing in file \"$mainFile\"", fileText.contains("<caret>"))

                        InTextDirectivesUtils.findStringWithPrefixes(fileText, "// MIN_JAVA_VERSION: ")?.let { minJavaVersion ->
                            if (!SystemInfo.isJavaVersionAtLeast(minJavaVersion)) return@configureRegistryAndRun
                        }

                        checkForErrorsBefore(fileText)

                        doTestFor(mainFile, pathToFiles, intentionAction, fileText)

                        checkForErrorsAfter(fileText)

                        PsiTestUtil.checkPsiStructureWithCommit(file, PsiTestUtil::checkPsiMatchesTextIgnoringNonCode)
                    } finally {
                        ConfigLibraryUtil.unconfigureLibrariesByDirective(module, fileText)
                    }
                }
            }
        }
    }

    protected open fun checkForErrorsAfter(fileText: String) {
        val file = this.file

        if (file is KtFile && isApplicableDirective(fileText) && !InTextDirectivesUtils.isDirectiveDefined(fileText, "// SKIP_ERRORS_AFTER")) {
            if (!InTextDirectivesUtils.isDirectiveDefined(fileText, "// SKIP_WARNINGS_AFTER")) {
                DirectiveBasedActionUtils.checkForUnexpectedWarnings(
                    file,
                    disabledByDefault = false,
                    directiveName = "AFTER-WARNING"
                )
            }

            DirectiveBasedActionUtils.checkForUnexpectedErrors(file)
        }
    }

    protected open fun checkForErrorsBefore(fileText: String) {
        val file = this.file

        if (file is KtFile && !InTextDirectivesUtils.isDirectiveDefined(fileText, "// SKIP_ERRORS_BEFORE")) {
            DirectiveBasedActionUtils.checkForUnexpectedErrors(file)
        }
    }

    private fun <T> computeUnderProgressIndicatorAndWait(compute: () -> T): T {
        val result = CompletableFuture<T>()
        val progressIndicator = ProgressIndicatorBase()
        try {
            val task = object : Task.Backgroundable(project, "isApplicable", false) {
                override fun run(indicator: ProgressIndicator) {
                    result.complete(compute())
                }
            }
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, progressIndicator)
            return result.get(10, TimeUnit.SECONDS)
        } finally {
            progressIndicator.cancel()
        }
    }

    @Throws(Exception::class)
    protected open fun doTestFor(mainFile: File, pathToFiles: Map<String, PsiFile>, intentionAction: IntentionAction, fileText: String) {
        val mainFilePath = mainFile.name
        val isApplicableExpected = isApplicableDirective(fileText)

        val isApplicableOnPooled = computeUnderProgressIndicatorAndWait {
            ApplicationManager.getApplication().runReadAction(Computable { intentionAction.isAvailable(project, editor, file) })
        }

        val isApplicableOnEdt = intentionAction.isAvailable(project, editor, file)

        Assert.assertEquals(
            "There should not be any difference what thread isApplicable is called from",
            isApplicableOnPooled,
            isApplicableOnEdt
        )

        Assert.assertTrue(
            "isAvailable() for " + intentionAction.javaClass + " should return " + isApplicableExpected,
            isApplicableExpected == isApplicableOnEdt
        )

        val intentionTextString = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// " + intentionTextDirectiveName() + ": ")

        if (intentionTextString != null) {
            TestCase.assertEquals("Intention text mismatch.", intentionTextString, intentionAction.text)
        }

        val shouldFailString = StringUtil.join(InTextDirectivesUtils.findListWithPrefixes(fileText, "// SHOULD_FAIL_WITH: "), ", ")

        try {
            if (isApplicableExpected) {
                val action = { intentionAction.invoke(project, editor, file) }
                if (intentionAction.startInWriteAction())
                    project.executeWriteCommand(intentionAction.text, action)
                else
                    project.executeCommand(intentionAction.text, null, action)

                // Don't bother checking if it should have failed.
                if (shouldFailString.isEmpty()) {
                    for ((filePath, value) in pathToFiles) {
                        val canonicalPathToExpectedFile = filePath + afterFileNameSuffix(mainFile)
                        val afterFile = testDataFile(canonicalPathToExpectedFile)
                        if (filePath == mainFilePath) {
                            try {
                                myFixture.checkResultByFile(canonicalPathToExpectedFile)
                            } catch (e: ComparisonFailure) {
                                KotlinTestUtils.assertEqualsToFile(afterFile, editor.document.text)
                            }
                        } else {
                            KotlinTestUtils.assertEqualsToFile(afterFile, value.text)
                        }
                    }
                }
            }
            TestCase.assertEquals("Expected test to fail.", "", shouldFailString)
        } catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
            TestCase.assertEquals("Failure message mismatch.", shouldFailString, StringUtil.join(e.messages.sorted(), ", "))
        } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            TestCase.assertEquals("Failure message mismatch.", shouldFailString, e.message?.replace('\n', ' '))
        }
    }

    companion object {
        private val EXTENSIONS = arrayOf(".kt", ".kts", ".java", ".groovy")
    }
}

