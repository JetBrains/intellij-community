// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.util

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.replaceWithBranch
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.codeinsight.utils.isReferenceToImplicitLambdaParameter
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.idea.base.psi.textRangeIn as _textRangeIn

fun KtCallElement.replaceOrCreateTypeArgumentList(newTypeArgumentList: KtTypeArgumentList) {
    if (typeArgumentList != null) typeArgumentList?.replace(newTypeArgumentList)
    else addAfter(
        newTypeArgumentList,
        calleeExpression,
    )
}

@Deprecated(
    "Please use org.jetbrains.kotlin.idea.base.psi.textRangeIn",
    ReplaceWith("textRangeIn(other)", "org.jetbrains.kotlin.idea.base.psi.textRangeIn")
)
fun PsiElement.textRangeIn(other: PsiElement): TextRange = _textRangeIn(other)

fun KtDotQualifiedExpression.calleeTextRangeInThis(): TextRange? = callExpression?.calleeExpression?.textRangeIn(this)

fun KtNamedDeclaration.nameIdentifierTextRangeInThis(): TextRange? = nameIdentifier?.textRangeIn(this)

fun PsiElement.hasComments(): Boolean = anyDescendantOfType<PsiComment>()

fun KtDotQualifiedExpression.hasNotReceiver(): Boolean {
    val element = getQualifiedElementSelector()?.mainReference?.resolve() ?: return false
    return element is KtClassOrObject ||
            element is KtConstructor<*> ||
            element is KtCallableDeclaration && element.receiverTypeReference == null && (element.containingClassOrObject is KtObjectDeclaration?) ||
            element is PsiMember && element.hasModifier(JvmModifier.STATIC) ||
            element is PsiMethod && element.isConstructor
}

val KtExpression.isUnitLiteral: Boolean
    get() = StandardNames.FqNames.unit.shortName() == (this as? KtNameReferenceExpression)?.getReferencedNameAsName()

val PsiElement.isAnonymousFunction: Boolean get() = this is KtNamedFunction && isAnonymousFunction

val KtNamedFunction.isAnonymousFunction: Boolean get() = nameIdentifier == null

val DeclarationDescriptor.isPrimaryConstructorOfDataClass: Boolean
    get() = this is ConstructorDescriptor && this.isPrimary && this.constructedClass.isData

fun findLambdaOpenLBraceForGeneratedIt(ref: PsiReference): PsiElement? {
    val element: PsiElement = ref.element
    if (element.text != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier) return null

    if (element !is KtNameReferenceExpression || !element.isReferenceToImplicitLambdaParameter()) return null

    val itDescriptor = element.resolveMainReferenceToDescriptors().singleOrNull() ?: return null
    val descriptorWithSource = itDescriptor.containingDeclaration as? DeclarationDescriptorWithSource ?: return null
    val lambdaExpression = descriptorWithSource.source.getPsi()?.parent as? KtLambdaExpression ?: return null
    return lambdaExpression.leftCurlyBrace.treeNext?.psi
}

internal fun KtExpression.replaceWithBranchAndMoveCaret(branch: KtExpression, isUsedAsExpression: Boolean, keepBraces: Boolean = false) {
    val originalExpression = this

    // TODO get rid of this caret model manipulation when all usages are migrated to Mod command - it doesn't work there 
    val caretModel = originalExpression.findExistingEditor()?.caretModel

    // This code can be called non-Mod command usages, so we have to allow calling it from EDT and write action
    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    val replaced = allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            originalExpression.replaceWithBranch(branch, isUsedAsExpression, keepBraces)
        }
    }

    if (replaced != null) {
        caretModel?.moveToOffset(replaced.startOffset)
    }
}