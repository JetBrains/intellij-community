// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.idea.completion.handlers.indexOfSkippingSpace
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.moveCaretIntoGeneratedElement
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import javax.swing.Icon

class OverridesCompletionLookupElementDecorator(
    lookupElement: LookupElement,
    private val declaration: KtCallableDeclaration?,
    private val text: String,
    private val isImplement: Boolean,
    private val icon: RowIcon,
    private val baseClassName: String,
    private val baseClassIcon: Icon?,
    private val isConstructorParameter: Boolean,
    private val classOrObject: KtClassOrObject,
    private val isSuspend: Boolean,
    private val generateMember: (targetClass: KtClassOrObject, copyDoc: Boolean) -> KtCallableDeclaration,
    private val shortenReferences: (KtElement) -> Unit,
) : LookupElementDecorator<LookupElement>(lookupElement) {
    override fun getLookupString() =
        if (declaration == null) "override" else delegate.lookupString // don't use "override" as lookup string when already in the name of declaration

    override fun getAllLookupStrings() = setOf(lookupString, delegate.lookupString)

    override fun renderElement(presentation: LookupElementPresentation) {
        super.renderElement(presentation)

        presentation.itemText = text
        presentation.isItemTextBold = isImplement
        presentation.icon = icon
        presentation.clearTail()
        presentation.setTypeText(baseClassName, baseClassIcon)
    }

    override fun getDelegateInsertHandler(): InsertHandler<LookupElement> = InsertHandler { context, _ ->
        val dummyMemberHead = when {
            declaration != null -> ""
            isConstructorParameter -> "override val "
            else -> "override fun "
        }
        val dummyMemberTail = when {
            isConstructorParameter || declaration is KtProperty -> "dummy: Dummy ,@"
            else -> "dummy() {}"
        }
        val dummyMemberText = dummyMemberHead + dummyMemberTail
        val override = KtTokens.OVERRIDE_KEYWORD.value

        tailrec fun calcStartOffset(startOffset: Int, diff: Int = 0): Int {
            return when {
                context.document.text[startOffset - 1].isWhitespace() -> calcStartOffset(startOffset - 1, diff + 1)
                context.document.text.substring(startOffset - override.length, startOffset) == override -> {
                    startOffset - override.length
                }
                else -> diff + startOffset
            }
        }

        val startOffset = calcStartOffset(context.startOffset)
        val tailOffset = context.tailOffset
        context.document.replaceString(startOffset, tailOffset, dummyMemberText)

        val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
        psiDocumentManager.commitDocument(context.document)

        val dummyMember = context.file.findElementAt(startOffset)!!.getStrictParentOfType<KtNamedDeclaration>()!!

        // keep original modifiers
        val psiFactory = KtPsiFactory(context.project)
        val modifierList = psiFactory.createModifierList(dummyMember.modifierList!!.text)

        fun isCommentOrWhiteSpace(e: PsiElement) = e is PsiComment || e is PsiWhiteSpace
        fun createCommentOrWhiteSpace(e: PsiElement) =
            if (e is PsiComment) psiFactory.createComment(e.text) else psiFactory.createWhiteSpace(e.text)
        val dummyMemberChildren = dummyMember.allChildren
        val headComments = dummyMemberChildren.takeWhile(::isCommentOrWhiteSpace).map(::createCommentOrWhiteSpace).toList()
        val tailComments = dummyMemberChildren.toList().takeLastWhile(::isCommentOrWhiteSpace).map(::createCommentOrWhiteSpace)

        val prototype = generateMember(classOrObject, false)
        prototype.modifierList!!.replace(modifierList)
        val insertedMember = dummyMember.replaced(prototype)
        if (isSuspend) insertedMember.addModifier(KtTokens.SUSPEND_KEYWORD)

        val insertedMemberParent = insertedMember.parent
        headComments.forEach { insertedMemberParent.addBefore(it, insertedMember) }
        tailComments.reversed().forEach { insertedMemberParent.addAfter(it, insertedMember) }

        shortenReferences(insertedMember)

        if (isConstructorParameter) {
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)

            val offset = insertedMember.endOffset
            val chars = context.document.charsSequence
            val commaOffset = chars.indexOfSkippingSpace(',', offset)!!
            val atCharOffset = chars.indexOfSkippingSpace('@', commaOffset + 1)!!
            context.document.deleteString(offset, atCharOffset + 1)

            context.editor.moveCaret(offset)
        } else {
            moveCaretIntoGeneratedElement(context.editor, insertedMember)
        }
    }
}