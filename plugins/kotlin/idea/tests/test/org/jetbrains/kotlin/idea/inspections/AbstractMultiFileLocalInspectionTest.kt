// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.jsonUtils.getString
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractMultiFileLocalInspectionTest : AbstractLocalInspectionTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        val testFile = File(testDataDirectory, fileName())
        val config = JsonParser.parseString(FileUtil.loadFile(testFile, true)) as JsonObject
        val withRuntime = config["withRuntime"]?.asBoolean ?: false
        return if (withRuntime)
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
        else
            KotlinLightProjectDescriptor.INSTANCE
    }

    open fun inspectionClassFieldName(): String = "inspectionClass"

    override fun doTest(path: String) {
        val testFile = File(path)
        val config = JsonParser.parseString(FileUtil.loadFile(testFile, true)) as JsonObject

        val mainFilePath = config.getString("mainFile")
        val mainFile = File(testFile.parent, "before/$mainFilePath")
        val mainFileText = FileUtil.loadFile(mainFile, true)
        TestCase.assertTrue("\"<caret>\" is missing in file \"$mainFilePath\"", mainFileText.contains("<caret>"))

        val inspection = Class.forName(config.getString(inspectionClassFieldName())).getDeclaredConstructor().newInstance() as LocalInspectionTool
        val problemExpectedString = config["problem"]?.asString // null means "some problem", "none" means no problem
        val localFixTextString = config["fix"]?.asString // null means "some single fix" or "none" if no problem expected
        val inspectionSettings = loadInspectionSettings(testFile)

        val expectedHighlightString = InTextDirectivesUtils.findStringWithPrefixes(
            mainFileText, "// $expectedProblemHighlightType: "
        )

        doTest(path) test@{
            myFixture.configureFromTempProjectFile(mainFilePath)
            runInspectionWithFixesAndCheck(inspection, problemExpectedString, expectedHighlightString, localFixTextString, inspectionSettings)
        }
    }

    protected fun doTest(path: String, action: (VirtualFile) -> Unit) {
        val beforeDir = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(testDataDirectory, File(path).parentFile)!!) + "/before"
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
