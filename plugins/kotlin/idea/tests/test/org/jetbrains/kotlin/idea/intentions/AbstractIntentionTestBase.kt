// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.hints.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiFile
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.formatter.FormatSettingsUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinAbstractHintsProvider
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File
import java.util.concurrent.ExecutionException
import kotlin.Throws

abstract class AbstractIntentionTestBase : KotlinLightCodeInsightFixtureTestCase() {
    protected open fun intentionFileName(): String = ".intention"

    protected open fun afterFileNameSuffix(ktFilePath: File): String = ".after"

    protected open fun isApplicableDirectiveName(): String = "IS_APPLICABLE"

    protected open fun isApplicableDirective(fileText: String): Boolean {
        val isApplicableString = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// ${isApplicableDirectiveName()}: ")
        return isApplicableString == null || isApplicableString == "true"
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { resetInlayHints(project) },
            ThrowableRunnable { super.tearDown() },
        )
    }

    private fun resetInlayHints(project: Project) {
        val language = KotlinLanguage.INSTANCE
        val providerInfos =
            InlayHintsProviderFactory.EP.extensionList
                .flatMap { it.getProvidersInfo() }
                .filter { it.language == language }
                .mapNotNull { it.provider as? KotlinAbstractHintsProvider<KotlinAbstractHintsProvider.HintsSettings> }
        providerInfos.forEach {
            val hintsSettings = InlayHintsSettings.instance()
            hintsSettings.storeSettings(it.key, language, it.createSettings())
        }
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
                "${intentionFileName()} file is not found for " + testDataFile +
                        "\nAdd it to base directory of test data. It should contain fully-qualified name of intention class."
            )

            1 -> {
                val className = FileUtil.loadFile(candidateFiles[0]).trim { it <= ' ' }
                val newInstance = Class.forName(className).getDeclaredConstructor().newInstance()
                return (newInstance as? ModCommandAction)?.asIntention() ?: newInstance as? IntentionAction ?: error("Class `$className` has to be IntentionAction or ModCommandAction")
            }

            else -> throw AssertionError(
                "Several ${intentionFileName()} files are available for $testDataFile\nPlease remove some of them\n$candidateFiles"
            )
        }
    }

    @Throws(Exception::class)
    protected open fun doTest(unused: String) {
        val mainFile = dataFile()
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

        val fileText = FileUtil.loadFile(mainFile, true)
        withCustomCompilerOptions(fileText, project, module) {
            val psiFiles = myFixture.configureByFiles(*sourceFilePaths.toTypedArray())
            val pathToFiles = mapOf(*(sourceFilePaths zip psiFiles).toTypedArray())

            ConfigLibraryUtil.configureLibrariesByDirective(module, fileText)
            if ((myFixture.file as? KtFile)?.isScript() == true) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
            }

            configureCodeStyleAndRun(project, { FormatSettingsUtil.createConfigurator(fileText, it).configureSettings() }) {
                configureRegistryAndRun(project, fileText) {
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

        val disableTestDirective = IgnoreTests.DIRECTIVES.of(pluginMode)
        if (file is KtFile &&
            isApplicableDirective(fileText) &&
            !InTextDirectivesUtils.isDirectiveDefined(fileText, "// SKIP_ERRORS_AFTER") &&
            !InTextDirectivesUtils.isDirectiveDefined(fileText, disableTestDirective)
        ) {
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

        val disableTestDirective = IgnoreTests.DIRECTIVES.of(pluginMode)
        if (file is KtFile &&
            !InTextDirectivesUtils.isDirectiveDefined(fileText, "// SKIP_ERRORS_BEFORE") &&
            !InTextDirectivesUtils.isDirectiveDefined(fileText, disableTestDirective)
        ) {
            DirectiveBasedActionUtils.checkForUnexpectedErrors(file)
        }
    }

    protected open fun doTestFor(mainFile: File, pathToFiles: Map<String, PsiFile>, intentionAction: IntentionAction, fileText: String) {
        val mainFilePath = mainFile.name
        val isApplicableExpected: Boolean = isApplicableDirective(fileText)

        val isApplicableOnPooled: Boolean = project.computeOnBackground {
            runReadAction{ intentionAction.isAvailable(project, editor, file) }
        }
        Assert.assertTrue(
            "isAvailable() for " + intentionAction.javaClass + " should return " + isApplicableExpected,
            isApplicableExpected == isApplicableOnPooled
        )

        val modCommandAction: ModCommandAction? = intentionAction.asModCommandAction()
        if (modCommandAction == null) {
            val isApplicableOnEdt = intentionAction.isAvailable(project, editor, file)

            Assert.assertTrue(
                "isAvailable() for " + intentionAction.javaClass + " should return " + isApplicableExpected,
                isApplicableExpected == isApplicableOnEdt
            )
        }

        DirectiveBasedActionUtils.checkPriority(fileText, intentionAction)

        val intentionTextString = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// " + intentionTextDirectiveName() + ": ")

        if (intentionTextString != null) {
            TestCase.assertEquals("Intention text mismatch.", intentionTextString, intentionAction.text)
        }

        val shouldFailString = StringUtil.join(InTextDirectivesUtils.findListWithPrefixes(fileText, "// SHOULD_FAIL_WITH: "), ", ")

        try {
            if (isApplicableExpected) {
                KotlinTestHelpers.registerChooserInterceptor(myFixture.testRootDisposable) { options -> options.last() }

                val action = { intentionAction.invoke(project, editor, file) }
                if (intentionAction.startInWriteAction()) {
                    project.executeWriteCommand(intentionAction.text, action)
                } else {
                    if (modCommandAction == null) {
                        project.executeCommand(intentionAction.text, null, action)
                    } else {
                        val actionContext = ActionContext.from(editor, file)
                        val command: ModCommand = project.computeOnBackground {
                            runReadAction {
                                modCommandAction.perform(actionContext)
                            }
                        }
                        project.executeCommand(intentionAction.text, null) {
                            ModCommandExecutor.getInstance().executeInteractively(actionContext, command, editor)
                        }
                    }
                }
                UIUtil.dispatchAllInvocationEvents()

                // Don't bother checking if it should have failed.
                if (shouldFailString.isEmpty()) {
                    for ((filePath, value) in pathToFiles) {
                        val canonicalPathToExpectedFile = filePath + afterFileNameSuffix(mainFile)
                        val afterFile = dataFile(canonicalPathToExpectedFile)
                        if (filePath == mainFilePath) {
                            try {
                                myFixture.checkResultByFile(canonicalPathToExpectedFile)
                            } catch (_: FileComparisonFailedError) {
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

@ApiStatus.Internal
fun <T> Project.computeOnBackground(compute: () -> T): T {
    try {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
            compute()
        }, "compute", true, this)
    } catch (e: ExecutionException) {
        throw e.cause!!
    }
}
