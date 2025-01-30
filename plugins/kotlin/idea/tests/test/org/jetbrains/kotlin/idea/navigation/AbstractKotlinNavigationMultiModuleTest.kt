// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.navigation

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.collectRelatedItems
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.codeInsight.GotoSuperActionHandler
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.navigation.NavigationTestUtils.getExpectedReferences
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.extractMarkerOffset
import org.jetbrains.kotlin.idea.test.findFileWithCaret
import java.io.File

abstract class AbstractKotlinNavigationMultiModuleTest : AbstractMultiModuleTest() {
    protected abstract fun doNavigate(editor: Editor, file: PsiFile): GotoTargetHandler.GotoData

    protected fun doTest(testDataDir: String) {
        setupMppProjectFromDirStructure(File(testDataDir))
        val file = project.findFileWithCaret()

        val doc = PsiDocumentManager.getInstance(myProject).getDocument(file)!!
        val offset = doc.extractMarkerOffset(project, "<caret>")
        val editor = EditorFactory.getInstance().createEditor(doc, myProject)
        editor.caretModel.moveToOffset(offset)
        try {
            val gotoData = doNavigate(editor, file)
            val documentText = editor.document.text
            val expectedReferences = if (pluginMode == KotlinPluginMode.K2 && documentText.contains("// K2_REF:")) {
                getExpectedReferences(documentText, "// K2_REF:")
            } else {
                getExpectedReferences(documentText, "// REF:")
            }
            NavigationTestUtils.assertGotoDataMatching(editor, gotoData, true, expectedReferences)
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }
}


abstract class AbstractKotlinGotoImplementationMultiModuleTest : AbstractKotlinNavigationMultiModuleTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("navigation/implementations/multiModule")

    override fun doNavigate(editor: Editor, file: PsiFile) = NavigationTestUtils.invokeGotoImplementations(editor, file)!!
}

abstract class AbstractKotlinGotoSuperMultiModuleTest : AbstractKotlinNavigationMultiModuleTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("navigation/gotoSuper/multiModule")

    override fun doNavigate(editor: Editor, file: PsiFile): GotoTargetHandler.GotoData {
        val (superDeclarations, _) = GotoSuperActionHandler.SuperDeclarationsAndDescriptor.forDeclarationAtCaret(editor, file)
        return GotoTargetHandler.GotoData(file.findElementAt(editor.caretModel.offset)!!, superDeclarations.toTypedArray(), emptyList())
    }
}

abstract class AbstractKotlinGotoRelatedSymbolMultiModuleTest : AbstractKotlinNavigationMultiModuleTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("navigation/relatedSymbols/multiModule")

    override fun doNavigate(editor: Editor, file: PsiFile): GotoTargetHandler.GotoData {
        val source = file.findElementAt(editor.caretModel.offset)!!
        val relatedItems =
            runBlocking {
                withBackgroundProgress(project, "Test Android Gradle Sync") {
                    readAction {
                        collectRelatedItems(contextElement = source, dataContext = SimpleDataContext.EMPTY_CONTEXT)
                    }
                }
            }
        return GotoTargetHandler.GotoData(source, relatedItems.map { it.element }.toTypedArray(), emptyList())
    }
}
