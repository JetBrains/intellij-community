// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsAndHashcodeAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.isAnyEquals
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.isAnyHashCode
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.isAnyToString
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private const val EQUALS = "equals"
private const val HASH_CODE = "hashCode"

class AbstractSuperCallFix(element: KtNameReferenceExpression) : KotlinPsiOnlyQuickFixAction<KtNameReferenceExpression>(element) {

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val expression = psiElement.safeAs<KtNameReferenceExpression>() ?: return emptyList()
            return listOf(AbstractSuperCallFix(expression))
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val expression = element ?: return false

        fun isToStringOverride() =
            expression.resolveToCall()?.resultingDescriptor?.safeAs<FunctionDescriptor>()
                ?.isAnyToString() == true

        return getSuperClassNameToReferTo(expression) != null && !isToStringOverride() // Just in case KTIJ-22784 is late
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val containingClass = expression.getNonStrictParentOfType<KtClassOrObject>() ?: return
        val functionDescriptor = expression.resolveToCall()?.resultingDescriptor?.safeAs<FunctionDescriptor>() ?: return

        fun replaceWithGenerated(clazz: KtClass) {
            val action = KotlinGenerateEqualsAndHashcodeAction()
            val membersInfo = action.prepareMembersInfo(clazz, project, false) ?: return

            val generated = when {
                functionDescriptor.isAnyHashCode() -> action.generateHashCode(project, membersInfo.adjust(needHashCodeActually = true), containingClass)
                functionDescriptor.isAnyEquals() -> action.generateEquals(project, membersInfo.adjust(needEqualsActually = true), containingClass)
                else -> error("$functionDescriptor is not expected")
            } ?: return

            expression.parentOfType<KtNamedFunction>()?.replace(generated)
                ?.safeAs<KtElement>()
                ?.let {
                    ShortenReferences.DEFAULT.process(it)
                }
        }

        fun replaceIfNotInObject() = containingClass.safeAs<KtClass>()?.let { replaceWithGenerated(it) }

        when {
            functionDescriptor.isAnyEquals() -> replaceIfNotInObject()
            functionDescriptor.isAnyHashCode() -> replaceIfNotInObject()
            else -> {
                getSuperClassNameToReferTo(expression)?.let { superClassName ->
                    expression.parentOfType<KtDotQualifiedExpression>()
                        ?.receiverExpression
                        ?.safeAs<KtSuperExpression>()
                        ?.specifySuperType(superClassName)

                    KtPsiFactory(containingClass).createSuperTypeEntry(superClassName)
                }?.let {
                    val alreadyExists = containingClass.superTypeListEntries.any { entry -> entry.text == it.text }
                    if (!alreadyExists) {
                        containingClass.addSuperTypeListEntry(it)
                    }
                }
            }
        }
    }

    override fun getText(): String {
        val expression = element ?: return ""
        return when (expression.getReferencedName()) {
            HASH_CODE -> KotlinBundle.message("hash.code.text")
            EQUALS -> KotlinBundle.message("equals.text")
            else -> {
                val nameToReferTo = getSuperClassNameToReferTo(expression) ?: error("isAvailable() was supposed to prevent null")
                KotlinBundle.message("specify.super.type", nameToReferTo)
            }
        }
    }

    override fun getFamilyName(): String = text
}

private fun KotlinGenerateEqualsAndHashcodeAction.Info.adjust(
    needEqualsActually: Boolean = this.needEquals,
    needHashCodeActually: Boolean = this.needHashCode
): KotlinGenerateEqualsAndHashcodeAction.Info = KotlinGenerateEqualsAndHashcodeAction.Info(
    needEqualsActually, needHashCodeActually, this.classDescriptor, this.variablesForEquals, this.variablesForHashCode
)

private fun KtSuperExpression.specifySuperType(superType: String) {
    val label = labelQualifier?.text ?: ""
    replace(KtPsiFactory(this).createExpression("super<$superType>$label"))
}

private fun getSuperClassNameToReferTo(expression: KtNameReferenceExpression): String? {
    fun tryViaCalledFunction(): CallableDescriptor? = expression.resolveToCall()?.resultingDescriptor
        ?.overriddenDescriptors
        ?.find { it.safeAs<SimpleFunctionDescriptor>()?.modality != Modality.ABSTRACT }

    fun tryViaContainingFunction(): CallableDescriptor? = expression.containingFunction()?.descriptor
        ?.safeAs<CallableDescriptor>()?.overriddenDescriptors
        ?.find { it.safeAs<MemberDescriptor>()?.modality != Modality.ABSTRACT }

    val callableToUseInstead = tryViaCalledFunction()
        ?: tryViaContainingFunction()
        ?: return null

    return callableToUseInstead.containingDeclaration.name.asString()
}