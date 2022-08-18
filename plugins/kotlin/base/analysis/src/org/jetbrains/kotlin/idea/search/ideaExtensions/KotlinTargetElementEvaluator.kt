// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.codeInsight.JavaTargetElementEvaluator
import com.intellij.codeInsight.TargetElementEvaluatorEx
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.TargetElementUtilExtender
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.util.BitUtil
import org.jetbrains.kotlin.idea.references.KtDestructuringDeclarationReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

abstract class KotlinTargetElementEvaluator : TargetElementEvaluatorEx, TargetElementUtilExtender {
    companion object {
        const val DO_NOT_UNWRAP_LABELED_EXPRESSION = 0x100
        const val BYPASS_IMPORT_ALIAS = 0x200
    }

    // Place caret after the open curly brace in lambda for generated 'it'
    abstract fun findLambdaOpenLBraceForGeneratedIt(ref: PsiReference): PsiElement?

    // Navigate to receiver element for this in extension declaration
    abstract fun findReceiverForThisInExtensionFunction(ref: PsiReference): PsiElement?

    override fun getAdditionalDefinitionSearchFlags() = 0

    override fun getAdditionalReferenceSearchFlags() = DO_NOT_UNWRAP_LABELED_EXPRESSION or BYPASS_IMPORT_ALIAS

    override fun getAllAdditionalFlags() = additionalDefinitionSearchFlags + additionalReferenceSearchFlags

    override fun includeSelfInGotoImplementation(element: PsiElement): Boolean = !(element is KtClass && element.isAbstract())

    override fun getElementByReference(ref: PsiReference, flags: Int): PsiElement? {
        if (ref is KtSimpleNameReference && ref.expression is KtLabelReferenceExpression) {
            val refTarget = ref.resolve() as? KtExpression ?: return null
            if (!BitUtil.isSet(flags, DO_NOT_UNWRAP_LABELED_EXPRESSION)) {
                return refTarget.getLabeledParent(ref.expression.getReferencedName()) ?: refTarget
            }
            return refTarget
        }

        if (!BitUtil.isSet(flags, BYPASS_IMPORT_ALIAS)) {
            (ref.element as? KtSimpleNameExpression)?.mainReference?.getImportAlias()?.let { return it }
        }

        // prefer destructing declaration entry to its target if element name is accepted
        if (ref is KtDestructuringDeclarationReference && BitUtil.isSet(flags, TargetElementUtil.ELEMENT_NAME_ACCEPTED)) {
            return ref.element
        }

        val refExpression = ref.element as? KtSimpleNameExpression
        val calleeExpression = refExpression?.getParentOfTypeAndBranch<KtCallElement> { calleeExpression }
        if (calleeExpression != null) {
            (ref.resolve() as? KtConstructor<*>)?.let {
                return if (flags and JavaTargetElementEvaluator().additionalReferenceSearchFlags != 0) it else it.containingClassOrObject
            }
        }

        if (BitUtil.isSet(flags, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED)) {
            return findLambdaOpenLBraceForGeneratedIt(ref)
                ?: findReceiverForThisInExtensionFunction(ref)
        }

        return null
    }

    override fun isIdentifierPart(file: PsiFile, text: CharSequence, offset: Int): Boolean {
        val elementAtCaret = file.findElementAt(offset)

        if (elementAtCaret?.node?.elementType == KtTokens.IDENTIFIER) return true
        // '(' is considered identifier part if it belongs to primary constructor without 'constructor' keyword
        return elementAtCaret?.getNonStrictParentOfType<KtPrimaryConstructor>()?.textOffset == offset
    }
}
