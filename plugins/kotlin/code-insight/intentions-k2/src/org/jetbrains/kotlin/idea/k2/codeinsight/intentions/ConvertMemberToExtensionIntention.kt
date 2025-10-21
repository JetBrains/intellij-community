// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.isFunInterface
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.unblockDocument
import org.jetbrains.kotlin.idea.quickfix.BodySelectionType
import org.jetbrains.kotlin.idea.quickfix.convertMemberToExtensionAndPrepareBodySelection
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpect
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.liftToExpect
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.hasBody
import org.jetbrains.kotlin.psi.psiUtil.siblings

private val LOG = Logger.getInstance(ConvertMemberToExtensionIntention::class.java)

class ConvertMemberToExtensionIntention : SelfTargetingRangeIntention<KtCallableDeclaration>(
    KtCallableDeclaration::class.java, KotlinBundle.messagePointer("convert.member.to.extension")
), LowPriorityAction {
    private fun isApplicable(element: KtCallableDeclaration): Boolean {
        val classBody = element.parent as? KtClassBody ?: return false
        val parentClass = classBody.parent as? KtClassOrObject ?: return false
        if (parentClass.isLocal) return false
        if (parentClass is KtObjectDeclaration && parentClass.isCompanion()) return false
        if ((parentClass as? KtClass)?.isFunInterface() == true && !element.hasBody()) return false
        if (element.receiverTypeReference != null) return false
        if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
        when (element) {
            is KtProperty -> if (element.hasInitializer() || element.hasDelegate()) return false
            is KtSecondaryConstructor -> return false
        }

        return true
    }

    override fun applicabilityRange(element: KtCallableDeclaration): TextRange? {
        val nameIdentifier = element.nameIdentifier ?: return null
        if (!isApplicable(element)) return null
        return nameIdentifier.textRange
    }

    override fun startInWriteAction(): Boolean = false

    override fun applyTo(element: KtCallableDeclaration, editor: Editor?) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return

        var allowExpected = true

        val actualsForExpect = ActionUtil.underModalProgress(element.project, KotlinBundle.message("progress.title.searching.for.expected.actual")) { liftToExpect(element)?.actualsForExpect() }
        actualsForExpect?.let {
            if (it.isEmpty()) {
                allowExpected = askIfExpectedIsAllowed()
            }
        }

        val (extension, bodyTypeToSelect) = convertMemberToExtensionAndPrepareBodySelection(element, allowExpected, KtFile::addImport)

        runWriteAction {
            editor?.apply {
                unblockDocument()

                if (extension.isValid) {

                    if (bodyTypeToSelect != BodySelectionType.NOTHING) {
                        val bodyToSelect = getBodyForSelection(extension, bodyTypeToSelect)

                        if (bodyToSelect != null) {
                            val range = bodyToSelect.textRange
                            moveCaret(range.startOffset, ScrollType.CENTER)

                            val parent = bodyToSelect.parent
                            val lastSibling = if (parent is KtBlockExpression) parent.rBrace?.siblings(forward = false, withItself = false)
                                ?.first { it !is PsiWhiteSpace }
                            else bodyToSelect.siblings(forward = true, withItself = false).lastOrNull()
                            val endOffset = lastSibling?.endOffset ?: range.endOffset
                            selectionModel.setSelection(range.startOffset, endOffset)
                        } else {
                            LOG.error(
                                "Extension created with new method body but this body was not found after document commit. Extension text: \"${extension.text}\""
                            )
                            moveCaret(extension.textOffset, ScrollType.CENTER)
                        }
                    } else {
                        moveCaret(extension.textOffset, ScrollType.CENTER)
                    }
                } else {
                    LOG.error("Extension invalidated during document commit. Extension text \"${extension.text}\"")
                }
            }
        }
    }

    private fun getBodyForSelection(extension: KtCallableDeclaration, bodyTypeToSelect: BodySelectionType): KtExpression? {
        fun selectBody(declaration: KtDeclarationWithBody): KtExpression? {

            if (!declaration.hasBody()) return extension

            return declaration.bodyExpression?.let {
                (it as? KtBlockExpression)?.statements?.singleOrNull() ?: it
            }
        }

        return when (bodyTypeToSelect) {
            BodySelectionType.FUNCTION -> (extension as? KtFunction)?.let { selectBody(it) }
            BodySelectionType.GETTER -> (extension as? KtProperty)?.getter?.let { selectBody(it) }
            BodySelectionType.SETTER -> (extension as? KtProperty)?.setter?.let { selectBody(it) }
            else -> null
        }
    }

    private fun askIfExpectedIsAllowed(): Boolean {
        return Messages.showYesNoDialog(
            KotlinBundle.message("do.you.want.to.make.new.extension.an.expected.declaration"), text, Messages.getQuestionIcon()
        ) == Messages.YES
    }

}