// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.TargetElementUtil.ELEMENT_NAME_ACCEPTED
import com.intellij.codeInsight.TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.util.io.FileUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.test.*
import java.io.File

abstract class AbstractInlineTest : KotlinLightCodeInsightFixtureTestCase() {
    protected fun doTest(unused: String) {
        val testDataFile = dataFile()
        val afterFile = dataFile("${fileName()}.after")

        val mainFileName = testDataFile.name
        val mainFileBaseName = FileUtil.getNameWithoutExtension(mainFileName)
        val extraFiles = testDataFile.parentFile.listFiles { _, name ->
            name != mainFileName && name.startsWith("$mainFileBaseName.") && (name.endsWith(".kt") || name.endsWith(".java"))
        } ?: emptyArray()

        val allFiles = (extraFiles + testDataFile).associateBy { myFixture.configureByFile(it.name) }
        val fileWithCaret = allFiles.values.singleOrNull { "<caret>" in it.readText() } ?: error("Must have one <caret>")
        val file = myFixture.configureByFile(fileWithCaret.name)

        withCustomCompilerOptions(file.text, project, module) {
            val afterFileExists = afterFile.exists()
            val targetElement = TargetElementUtil.findTargetElement(
                myFixture.editor,
                ELEMENT_NAME_ACCEPTED or REFERENCED_ELEMENT_ACCEPTED
            )

            val handler = if (targetElement != null)
                InlineActionHandler.EP_NAME.extensions.firstOrNull { it.canInlineElement(targetElement) }
            else
                null

            val expectedErrors = InTextDirectivesUtils.findLinesWithPrefixesRemoved(myFixture.file.text, "// ERROR: ")
            val inlinePropertyKeepValue = InTextDirectivesUtils.getPrefixedBoolean(myFixture.file.text, "// INLINE_PROPERTY_KEEP: ")
            val settings = KotlinCommonRefactoringSettings.getInstance()
            val oldInlinePropertyKeepValue = settings.INLINE_PROPERTY_KEEP
            if (handler != null) {
                try {
                    inlinePropertyKeepValue?.let { settings.INLINE_PROPERTY_KEEP = it }
                    handler.inlineElement(project, editor, targetElement)
                    for ((extraPsiFile, extraFile) in allFiles) {
                        KotlinTestUtils.assertEqualsToFile(File("${extraFile.path}.after"), extraPsiFile.text)
                    }
                } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
                    TestCase.assertFalse("Refactoring not available: ${e.message}", afterFileExists)
                    TestCase.assertEquals("Expected errors", 1, expectedErrors.size)
                    TestCase.assertEquals("Error message", expectedErrors[0].replace("\\n", "\n"), e.message)
                } catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
                    TestCase.assertFalse("Conflicts: ${e.message}", afterFileExists)
                    TestCase.assertEquals("Expected errors", 1, expectedErrors.size)
                    TestCase.assertEquals("Error message", expectedErrors[0].replace("\\n", "\n"), e.message)
                } finally {
                    settings.INLINE_PROPERTY_KEEP = oldInlinePropertyKeepValue
                }
            } else {
                TestCase.assertFalse("No refactoring handler available", afterFileExists)
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}

abstract class AbstractInlineTestWithSomeDescriptors : AbstractInlineTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor = getProjectDescriptorFromFileDirective()
}
