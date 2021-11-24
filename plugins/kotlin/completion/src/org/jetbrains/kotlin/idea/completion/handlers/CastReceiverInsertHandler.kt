// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.completion.DeclarationLookupObject
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.*

object CastReceiverInsertHandler {
    fun postHandleInsert(context: InsertionContext, item: LookupElement) {
        val expression =
            PsiTreeUtil.findElementOfClassAtOffset(context.file, context.startOffset, KtSimpleNameExpression::class.java, false)
        val qualifiedExpression = PsiTreeUtil.getParentOfType(expression, KtQualifiedExpression::class.java, true)
        if (qualifiedExpression != null) {
            val receiver = qualifiedExpression.receiverExpression

            val descriptor = (item.`object` as? DeclarationLookupObject)?.descriptor as CallableDescriptor
            val project = context.project

            val thisObj =
                if (descriptor.extensionReceiverParameter != null) descriptor.extensionReceiverParameter else descriptor.dispatchReceiverParameter
            val fqName = IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(thisObj!!.type.constructor.declarationDescriptor!!)

            val parentCast = KtPsiFactory(project).createExpression("(expr as $fqName)") as KtParenthesizedExpression
            val cast = parentCast.expression as KtBinaryExpressionWithTypeRHS
            cast.left.replace(receiver)

            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            psiDocumentManager.commitDocument(context.document)
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)

            val expr = receiver.replace(parentCast) as KtParenthesizedExpression

            ShortenReferences.DEFAULT.process((expr.expression as KtBinaryExpressionWithTypeRHS).right!!)
        }
    }
}