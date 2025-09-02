// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.generate

import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestActionEvent
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.platforms.forcedTargetPlatform
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK2Mode
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractCodeInsightActionTest : KotlinLightCodeInsightFixtureTestCase() {
    protected open fun createAction(fileText: String): CodeInsightAction {
        val actionClassName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// ACTION_CLASS: ")
        return Class.forName(actionClassName).getDeclaredConstructor().newInstance() as CodeInsightAction
    }

    protected open fun configure(mainFilePath: String, mainFileText: String) {
        myFixture.configureByFile(mainFilePath) as KtFile
    }

    protected open fun checkExtra() {

    }

    protected open fun testAction(action: AnAction): Presentation {
        val e = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, e)
        if (e.presentation.isEnabled) {
            ActionUtil.performAction(action, e)
        }
        return e.presentation
    }

    protected open fun doTest(path: String) {
        val fileText = FileUtil.loadFile(dataFile(), true)

        val conflictFile = File("$path.messages")
        val afterFile = getAfterFile(path)

        var mainPsiFile: KtFile? = null

        try {
            ConfigLibraryUtil.configureLibrariesByDirective(module, fileText)

            val mainFile = dataFile()
            val mainFileName = mainFile.name
            val fileNameBase = mainFile.nameWithoutExtension + "."
            val rootDir = mainFile.parentFile
            rootDir
                .list { _, name ->
                    name.startsWith(fileNameBase) && name != mainFileName && (name.endsWith(".kt") || name.endsWith(".java"))
                }
                .forEach {
                    myFixture.configureByFile(it)
                }

            configure(fileName(), fileText)
            mainPsiFile = myFixture.file as KtFile

            val targetPlatformName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// PLATFORM: ")
            if (targetPlatformName != null) {
                val targetPlatform = when (targetPlatformName) {
                    "JVM" -> JvmPlatforms.unspecifiedJvmPlatform
                    "JavaScript" -> JsPlatforms.defaultJsPlatform
                    "Common" -> CommonPlatforms.defaultCommonPlatform
                    else -> error("Unexpected platform name: $targetPlatformName")
                }
                mainPsiFile.forcedTargetPlatform = targetPlatform
            }

            val action = createAction(fileText)

            val isApplicableExpected = !InTextDirectivesUtils.isDirectiveDefined(fileText, "// NOT_APPLICABLE")

            val presentation = testAction(action)
            TestCase.assertEquals(isApplicableExpected, presentation.isEnabled)

            assert(!conflictFile.exists()) { "Conflict file $conflictFile should not exist" }

            if (isApplicableExpected) {
                assertTrue(afterFile.exists())
                myFixture.checkResult(FileUtil.loadFile(afterFile, true))
                checkExtra()
            }
        } catch (e: FileComparisonFailedError) {
            KotlinTestUtils.assertEqualsToFile(afterFile, myFixture.editor)
        } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            KotlinTestUtils.assertEqualsToFile(conflictFile, e.message!!)
        } finally {
            mainPsiFile?.forcedTargetPlatform = null
            ConfigLibraryUtil.unconfigureLibrariesByDirective(module, fileText)
        }
    }

    private fun getAfterFile(path: String): File {
        if (isK2Mode()) {
            File("$path.k2.after").takeIf { it.exists() }?.let { return it }
        }
        return File("$path.after")
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}
