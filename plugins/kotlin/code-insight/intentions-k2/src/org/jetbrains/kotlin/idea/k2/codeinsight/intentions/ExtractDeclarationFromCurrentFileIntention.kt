// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationDelegate
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveDialog
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveModel
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class ExtractDeclarationFromCurrentFileIntention : SelfTargetingRangeIntention<KtClassOrObject>(
    KtClassOrObject::class.java,
    KotlinBundle.lazyMessage("intention.extract.declarations.from.file.text")
), LowPriorityAction {
    override fun applicabilityRange(element: KtClassOrObject): TextRange? {
        if (element.name == null) return null
        if (element.parent !is KtFile) return null
        if (element.hasModifier(KtTokens.PRIVATE_KEYWORD)) return null
        if (element.containingKtFile.run { declarations.size == 1 || containingDirectory === null }) return null

        val startOffset = when (element) {
            is KtClass -> element.startOffset
            is KtObjectDeclaration -> element.getObjectKeyword()?.startOffset
            else -> return null
        } ?: return null

        val endOffset = element.nameIdentifier?.endOffset ?: return null

        setTextGetter(
            KotlinBundle.lazyMessage(
                "intention.extract.declarations.from.file.text.details",
                element.name.toString(),
                0
            )
        )

        return TextRange(startOffset, endOffset)
    }

    override fun startInWriteAction() = false

    override fun applyTo(element: KtClassOrObject, editor: Editor?) {
        requireNotNull(editor) { "This intention requires an editor" }
        val containingFile = element.containingKtFile
        val project = containingFile.project
        val originalOffset = editor.caretModel.offset - element.startOffset
        val directory = containingFile.containingDirectory ?: return
        val packageName = containingFile.packageFqName
        val targetFileName = "${element.name}.kt"
        val targetFile = directory.findFile(targetFileName)

        val moveCallBack = MoveCallback {
            val newFile = directory.findFile(targetFileName) as KtFile
            val newDeclaration = newFile.declarations.first()
            activateFileWithPsiElement(newFile)
            FileEditorManager.getInstance(project).selectedTextEditor?.moveCaret(newDeclaration.startOffset + originalOffset)
        }

        if (targetFile != null) {
            if (isUnitTestMode()) {
                throw CommonRefactoringUtil.RefactoringErrorHintException(RefactoringBundle.message("file.already.exist", targetFileName))
            }
            // If an automatic move is not possible, fall back to full-fledged Move Declarations refactoring
            showRefactoringDialog(project, editor, element, targetFile, moveCallBack)
            return
        }


        val moveDescriptor = K2MoveDescriptor.Declarations(
            project,
            K2MoveSourceDescriptor.ElementSource(setOf(element)),
            K2MoveTargetDescriptor.File(targetFileName, packageName, directory)
        )

        K2MoveOperationDescriptor.Declarations(
            project,
            listOf(moveDescriptor),
            searchForText = false,
            searchInComments = false,
            searchReferences = true,
            dirStructureMatchesPkg = false,
            moveDeclarationsDelegate = K2MoveDeclarationDelegate.TopLevel,
            moveCallBack
        ).refactoringProcessor().run()
    }

    private fun showRefactoringDialog(
        project: Project,
        editor: Editor,
        element: KtClassOrObject,
        targetFile: PsiFile,
        callBack: MoveCallback
    ) {
        val model = K2MoveModel.create(arrayOf(element), targetFile, editor, callBack) ?: return
        val dialog = K2MoveDialog(project, model)
        dialog.show()
    }
}