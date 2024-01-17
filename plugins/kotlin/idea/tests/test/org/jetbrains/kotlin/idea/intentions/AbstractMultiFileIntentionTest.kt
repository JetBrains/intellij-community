// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.jsonUtils.getNullableString
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.junit.Assert
import java.io.File

abstract class AbstractMultiFileIntentionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        val testFile = File(testDataDirectory, fileName())
        val config = JsonParser.parseString(FileUtil.loadFile(testFile, true)) as JsonObject
        val withRuntime = config["withRuntime"]?.asBoolean ?: false
        return if (withRuntime)
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
        else
            KotlinLightProjectDescriptor.INSTANCE
    }

    protected fun doTest(path: String) {
        val testFile = File(path)
        val config = JsonParser.parseString(FileUtil.loadFile(testFile, true)) as JsonObject
        val mainFilePath = config.getString("mainFile")
        val intentionAction = Class.forName(config.getString("intentionClass")).getDeclaredConstructor().newInstance() as IntentionAction
        val isApplicableExpected = config["isApplicable"]?.asBoolean ?: true

        doTest(path) { rootDir ->
            val mainFile = myFixture.configureFromTempProjectFile(mainFilePath)
            val conflictFile = rootDir.findFileByRelativePath("$mainFilePath.conflicts")

            try {
                Assert.assertTrue(
                    "isAvailable() for ${intentionAction::class.java} should return $isApplicableExpected",
                    isApplicableExpected == intentionAction.isAvailable(project, editor, mainFile)
                )
                config.getNullableString("intentionText")?.let {
                    TestCase.assertEquals("Intention text mismatch", it, intentionAction.text)
                }

                if (isApplicableExpected) {
                    val action = { intentionAction.invoke(project, editor, mainFile) }
                    if (intentionAction.startInWriteAction())
                        project.executeWriteCommand(intentionAction.text, action)
                    else
                        project.executeCommand(intentionAction.text, null, action)
                }

                assert(conflictFile == null) { "Conflict file $conflictFile should not exist" }
            } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
                val expectedConflicts = LoadTextUtil.loadText(conflictFile!!).toString().trim()
                assertEquals(expectedConflicts, e.message)
            }
        }
    }

    protected fun doTest(path: String, action: (VirtualFile) -> Unit) {
        val relativePath = FileUtil.getRelativePath(testDataDirectory, File(path)) ?: error("$path is not under $testDataDirectory")
        val beforeDir = FileUtil.toSystemIndependentName(relativePath).substringBeforeLast('/') + "/before"
        val beforeVFile = myFixture.copyDirectoryToProject(beforeDir, "")
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()

        val afterDir = beforeDir.substringBeforeLast("/") + "/after"
        val afterDirIOFile = File(testDataDirectory, afterDir)
        val afterVFile = LocalFileSystem.getInstance().findFileByIoFile(afterDirIOFile)!!
        UsefulTestCase.refreshRecursively(afterVFile)

        action(beforeVFile)

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
        PlatformTestUtil.assertDirectoriesEqual(afterVFile, beforeVFile)
    }
}