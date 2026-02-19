// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.core.moveCaretIntoGeneratedElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import javax.swing.Icon

class ActualCompletionLookupElementDecorator(
    lookupElement: LookupElement,
    private val text: String,
    private val icon: RowIcon,
    private val baseClassName: String?,
    private val baseClassIcon: Icon?,
    private val isSuspend: Boolean,
    private val generateMember: () -> KtDeclaration,
    private val shortenReferences: (KtElement) -> Unit,
    private val declarationLookupObject: Any? = null,
    private val declaration: KtDeclaration? = null,
) : LookupElementDecorator<LookupElement>(lookupElement) {
    private val actualKeyword: String = KtTokens.ACTUAL_KEYWORD.value

    override fun getObject(): Any {
        if (declarationLookupObject == null) return super.getObject()
        return declarationLookupObject
    }

    override fun getLookupString(): String = if (declaration == null) actualKeyword else delegate.lookupString

    override fun getAllLookupStrings() = setOf(lookupString, delegate.lookupString)

    override fun renderElement(presentation: LookupElementPresentation) {
        super.renderElement(presentation)

        presentation.itemText = text
        presentation.icon = icon
        presentation.clearTail()
        presentation.setTypeText(baseClassName, baseClassIcon)
    }

    override fun getDelegateInsertHandler(): InsertHandler<LookupElement> = InsertHandler { context, _ ->
        val dummyMemberHead = if (declaration == null) "$actualKeyword fun " else ""
        val dummyMemberTail = "dummy() {}"
        val dummyMemberText = dummyMemberHead + dummyMemberTail
        val actual = KtTokens.ACTUAL_KEYWORD.value

        tailrec fun calcStartOffset(startOffset: Int, diff: Int = 0): Int {
            return when {
                context.document.text[startOffset - 1].isWhitespace() -> calcStartOffset(startOffset - 1, diff + 1)
                context.document.text.substring(startOffset - actual.length, startOffset) == actual -> {
                    startOffset - actual.length
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

        val psiFactory = KtPsiFactory(context.project)
        val modifierList = psiFactory.createModifierList(dummyMember.modifierList!!.text)

        fun isCommentOrWhiteSpace(e: PsiElement) = e is PsiComment || e is PsiWhiteSpace
        fun createCommentOrWhiteSpace(e: PsiElement) =
            if (e is PsiComment) psiFactory.createComment(e.text) else psiFactory.createWhiteSpace(e.text)

        val dummyMemberChildren = dummyMember.allChildren
        val headComments = dummyMemberChildren.takeWhile(::isCommentOrWhiteSpace).map(::createCommentOrWhiteSpace).toList()
        val tailComments = dummyMemberChildren.toList().takeLastWhile(::isCommentOrWhiteSpace).map(::createCommentOrWhiteSpace)

        val prototype = generateMember()
        prototype.modifierList!!.replace(modifierList)
        val insertedMember = dummyMember.replaced(prototype)
        if (isSuspend) insertedMember.addModifier(KtTokens.SUSPEND_KEYWORD)

        val insertedMemberParent = insertedMember.parent
        headComments.forEach { insertedMemberParent.addBefore(it, insertedMember) }
        tailComments.reversed().forEach { insertedMemberParent.addAfter(it, insertedMember) }

        shortenReferences(insertedMember)

        moveCaretIntoGeneratedElement(context.editor, insertedMember)
    }
}