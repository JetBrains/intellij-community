// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset



    fun wrapLookupElementForStringTemplateQualifiedThis(originLookupElement: LookupElement ): LookupElement {
        return LookupElementDecorator.withDelegateInsertHandler(originLookupElement) { insertionContext: InsertionContext, lookupElement: LookupElement ->
            insertStringTemplateBraces(insertionContext, lookupElement)
        }
    }

    private fun insertStringTemplateBraces(insertionContext: InsertionContext, lookupElement: LookupElement) {
        val document = insertionContext.document
        val startOffset = insertionContext.startOffset

        val psiDocumentManager = PsiDocumentManager.getInstance(insertionContext.project)
        psiDocumentManager.commitAllDocuments()

        val token = getToken(insertionContext.file, document.charsSequence, startOffset)?: return
        val nameRef = token.parent as KtNameReferenceExpression

        document.insertString(nameRef.startOffset, "{")

        val tailOffset = insertionContext.tailOffset
        document.insertString(tailOffset, "}")
        insertionContext.tailOffset = tailOffset

        lookupElement.handleInsert(insertionContext)
    }

    private fun getToken(file: PsiFile, charsSequence: CharSequence, startOffset: Int): PsiElement? {
        assert(startOffset > 1 && charsSequence[startOffset +4] == '@'){return null}


        val token = file.findElementAt(startOffset )!!
        return token



    }

