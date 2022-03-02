// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.hierarchy

import com.intellij.ide.hierarchy.*
import com.intellij.ide.hierarchy.actions.BrowseHierarchyActionBase
import com.intellij.ide.hierarchy.call.CallerMethodsTreeStructure
import com.intellij.ide.hierarchy.type.SubtypesHierarchyTreeStructure
import com.intellij.ide.hierarchy.type.SupertypesHierarchyTreeStructure
import com.intellij.ide.hierarchy.type.TypeHierarchyTreeStructure
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.*
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.rt.execution.junit.ComparisonDetailsExtractor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ArrayUtil
import junit.framework.ComparisonFailure
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.KotlinHierarchyViewTestBase
import org.jetbrains.kotlin.idea.hierarchy.calls.KotlinCalleeTreeStructure
import org.jetbrains.kotlin.idea.hierarchy.calls.KotlinCallerTreeStructure
import org.jetbrains.kotlin.idea.hierarchy.overrides.KotlinOverrideTreeStructure
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.createTextEditorBasedDataContext
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

/*
Test Hierarchy view
Format: test build hierarchy for element at caret, file with caret should be the first in the sorted list of files.
Test accept more than one file, file extension should be .java or .kt
 */
abstract class AbstractHierarchyTest : KotlinHierarchyViewTestBase() {
    protected var folderName: String? = null

    private fun doHierarchyTest(folderName: String, structure: () -> HierarchyTreeStructure) {
        this.folderName = folderName
        doHierarchyTest(structure, *filesToConfigure)
    }

    protected fun doTypeClassHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        TypeHierarchyTreeStructure(
            project,
            getElementAtCaret(LanguageTypeHierarchy.INSTANCE) as PsiClass,
            HierarchyBrowserBaseEx.SCOPE_PROJECT
        )
    }

    protected fun doSuperClassHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        SupertypesHierarchyTreeStructure(
            project,
            getElementAtCaret(LanguageTypeHierarchy.INSTANCE) as PsiClass
        )
    }

    protected fun doSubClassHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        SubtypesHierarchyTreeStructure(
            project,
            getElementAtCaret(LanguageTypeHierarchy.INSTANCE) as PsiClass,
            HierarchyBrowserBaseEx.SCOPE_PROJECT
        )
    }

    protected fun doCallerHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        KotlinCallerTreeStructure(
            (getElementAtCaret(LanguageCallHierarchy.INSTANCE) as KtElement),
            HierarchyBrowserBaseEx.SCOPE_PROJECT
        )
    }

    protected fun doCallerJavaHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        CallerMethodsTreeStructure(
            project,
            getElementAtCaret(LanguageCallHierarchy.INSTANCE) as PsiMember,
            HierarchyBrowserBaseEx.SCOPE_PROJECT
        )
    }

    protected fun doCalleeHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        KotlinCalleeTreeStructure(
            (getElementAtCaret(LanguageCallHierarchy.INSTANCE) as KtElement),
            HierarchyBrowserBaseEx.SCOPE_PROJECT
        )
    }

    protected fun doOverrideHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        KotlinOverrideTreeStructure(
            project,
            (getElementAtCaret(LanguageMethodHierarchy.INSTANCE) as KtCallableDeclaration)
        )
    }

    private fun getElementAtCaret(extension: LanguageExtension<HierarchyProvider>): PsiElement {
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        val dataContext = createTextEditorBasedDataContext(project, editor, editor.caretModel.currentCaret)
        return BrowseHierarchyActionBase.findProvider(extension, file, file, dataContext)?.getTarget(dataContext)
            ?: throw RefactoringErrorHintException("Cannot apply action for element at caret")
    }

    protected val filesToConfigure: Array<String>
        get() {
            val folderName = folderName ?: error("folderName should be initialized")
            val files: MutableList<String> = ArrayList(2)
            FileUtil.processFilesRecursively(
                File(folderName)
            ) { file ->
                val fileName = file.name
                if (fileName.endsWith(".kt") || fileName.endsWith(".java")) {
                    files.add(fileName)
                }
                true
            }
            files.sort()
            return ArrayUtil.toStringArray(files)
        }

    @Throws(Exception::class)
    override fun doHierarchyTest(
        treeStructureComputable: Computable<out HierarchyTreeStructure>,
        vararg fileNames: String
    ) {
        try {
            super.doHierarchyTest(treeStructureComputable, *fileNames)
        } catch (e: RefactoringErrorHintException) {
            val file = File(folderName, "messages.txt")
            if (file.exists()) {
                val expectedMessage = FileUtil.loadFile(file, true)
                TestCase.assertEquals(expectedMessage, e.localizedMessage)
            } else {
                TestCase.fail("Unexpected error: " + e.localizedMessage)
            }
        } catch (failure: ComparisonFailure) {
            val actual = ComparisonDetailsExtractor.getActual(failure)
            val verificationFilePath = testDataPath + "/" + getTestName(false) + "_verification.xml"
            KotlinTestUtils.assertEqualsToFile(File(verificationFilePath), actual)
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    override val testDataDirectory: File
        get() {
            val testRoot = super.testDataDirectory
            val testDir = KotlinTestUtils.getTestDataFileName(this.javaClass, name) ?: error("Test data file name is missing")
            return File(testRoot, testDir)
        }
}