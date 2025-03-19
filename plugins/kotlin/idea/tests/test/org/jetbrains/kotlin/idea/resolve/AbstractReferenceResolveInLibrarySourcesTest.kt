// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.resolve

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.vfs.getPsiFile
import com.intellij.util.ThrowableRunnable
import junit.framework.AssertionFailedError
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import kotlin.test.assertNotEquals

abstract class AbstractReferenceResolveInLibrarySourcesTest : KotlinLightCodeInsightFixtureTestCase() {
    companion object {
        private const val REF_CARET_MARKER = "<ref-caret>"
    }

    private val mockLibraryFacility = MockLibraryFacility(
        source = IDEA_TEST_DATA_DIR.resolve("resolve/referenceInLib/inLibrarySource"),
    )

    override fun setUp() {
        super.setUp()
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    fun doTest(unused: String) {
        val fixture = myFixture!!

        fixture.configureByFile(fileName())

        val fileText = fixture.file!!.text
        val expectedReferences = AbstractReferenceResolveTest.getExpectedReferences(fileText, 0, "REF")
        val expectedResolveData = AbstractReferenceResolveTest.readResolveData(fileText, expectedReferences)

        myFixture.performEditorAction("GotoDeclaration")
        val fileEditorManager = FileEditorManager.getInstance(myFixture.project) as FileEditorManagerEx
        val currentEditor = fileEditorManager.selectedTextEditor ?: throw AssertionFailedError("No text editor is selected")
        val testedElementFile = currentEditor.virtualFile.getPsiFile(project)
        assertNotEquals(myFixture.file, testedElementFile, "There were no navigation from initial file to library made")

        val lineContext = InTextDirectivesUtils.findStringWithPrefixes(fileText, "CONTEXT:")
            ?: throw AssertionFailedError("'CONTEXT: ' directive is expected to set up position in library file: ${testedElementFile.name}")

        val inContextOffset = lineContext.indexOf(REF_CARET_MARKER)
        check(inContextOffset != -1) { "No '$REF_CARET_MARKER' marker found in 'CONTEXT: $lineContext'" }

        val contextStr = lineContext.replace(REF_CARET_MARKER, "")
        val offsetInFile = testedElementFile.text!!.indexOf(contextStr)
        check(offsetInFile != -1) { "Context '$contextStr' wasn't found in file ${testedElementFile.name}" }

        val offset = offsetInFile + inContextOffset

        val reference = testedElementFile.findReferenceAt(offset)!!

        AbstractReferenceResolveTest.checkReferenceResolve(expectedResolveData, offset, reference)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}
