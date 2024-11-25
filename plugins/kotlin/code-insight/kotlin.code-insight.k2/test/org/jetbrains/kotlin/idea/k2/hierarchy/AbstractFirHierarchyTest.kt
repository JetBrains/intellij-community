// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hierarchy

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.hierarchy.*
import com.intellij.ide.hierarchy.actions.BrowseHierarchyActionBase
import com.intellij.ide.hierarchy.call.CallerMethodsTreeStructure
import com.intellij.ide.hierarchy.type.TypeHierarchyTreeStructure
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ArrayUtil
import junit.framework.ComparisonFailure
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.KotlinHierarchyViewTestBase
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.calls.KotlinCalleeTreeStructure
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.calls.KotlinCallerTreeStructure
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.overrides.KotlinOverrideTreeStructure
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinSubtypesHierarchyTreeStructure
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinSupertypesHierarchyTreeStructure
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinTypeHierarchyTreeStructure
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.createTextEditorBasedDataContext
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import java.io.File

abstract class AbstractFirHierarchyTest : KotlinHierarchyViewTestBase() {
    protected var folderName: String? = null

    private fun doHierarchyTest(folderName: String, structure: () -> HierarchyTreeStructure) {
        this.folderName = folderName
        doHierarchyTest(structure, *filesToConfigure)
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

    protected fun doTypeClassHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        val element = getElementAtCaret(LanguageTypeHierarchy.INSTANCE)

        when (element) {
            is KtClassOrObject ->
                KotlinTypeHierarchyTreeStructure(
                    project,
                    element,
                    HierarchyBrowserBaseEx.SCOPE_PROJECT
                )
            else ->
                TypeHierarchyTreeStructure(project, element as PsiClass, HierarchyBrowserBaseEx.SCOPE_PROJECT)
        }
    }

    protected fun doSuperClassHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        KotlinSupertypesHierarchyTreeStructure(
            project,
            getElementAtCaret(LanguageTypeHierarchy.INSTANCE) as KtClassOrObject
        )
    }

    protected fun doSubClassHierarchyTest(folderName: String) = doHierarchyTest(folderName) {
        KotlinSubtypesHierarchyTreeStructure(
            project,
            getElementAtCaret(LanguageTypeHierarchy.INSTANCE) as KtClassOrObject,
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
            ?: BrowseHierarchyActionBase.findProvider(extension, TargetElementUtil.findTargetElement(editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.ELEMENT_NAME_ACCEPTED), file, dataContext)?.getTarget(dataContext)
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
            val actual = failure.actual
            var verificationFile = File(testDataDirectory, getTestName(false) + "_k2_verification.xml")
            if (!verificationFile.exists()) {
                verificationFile = File(testDataDirectory, getTestName(false) + "_verification.xml")
            }
            KotlinTestUtils.assertEqualsToFile(verificationFile, actual)
        }
    }

    override fun loadExpectedStructure(): String {
        var verificationFile = File(testDataDirectory, getTestName(false) + "_k2_verification.xml")
        if (!verificationFile.exists()) {
            verificationFile = File(testDataDirectory, getTestName(false) + "_verification.xml")
        }
        return verificationFile.readText()
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override val testDataDirectory: File
        get() {
            val testRoot = super.testDataDirectory
            val testDir = KotlinTestUtils.getTestDataFileName(this.javaClass, name) ?: error("Test data file name is missing")
            return File(testRoot, testDir)
        }
}