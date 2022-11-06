// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsAndHashcodeAction
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsAndHashcodeAction.Info
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateToStringAction
import org.jetbrains.kotlin.idea.base.psi.replaced
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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private const val EQUALS = "equals"
private const val HASH_CODE = "hashCode"
private const val TO_STRING = "toString"

class AbstractSuperCallFix(element: KtNameReferenceExpression) : KotlinPsiOnlyQuickFixAction<KtNameReferenceExpression>(element) {

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val expression = psiElement.safeAs<KtNameReferenceExpression>() ?: return emptyList()
            return listOf(AbstractSuperCallFix(expression))
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val expression = element ?: return false
        return getSuperClassFqNameToReferTo(expression) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val containingClass = expression.getNonStrictParentOfType<KtClassOrObject>() ?: return
        val functionDescriptor = expression.resolveToCall()?.resultingDescriptor?.safeAs<FunctionDescriptor>() ?: return

        fun replaceWithGenerated(clazz: KtClass) {
            val generated = when {
                functionDescriptor.isAnyHashCode() -> generateHashCode(clazz, project)
                functionDescriptor.isAnyEquals() -> generateEquals(clazz, project)
                functionDescriptor.isAnyToString() -> generateToString(clazz, project)
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
            functionDescriptor.isAnyToString() -> replaceIfNotInObject()
            else -> {
                val superExpression = expression.getParentSuperExpression()
                val superClassFqName = getSuperClassFqNameToReferTo(expression)
                if (superExpression != null && superClassFqName != null) {
                    superExpression.specifySuperType(superClassFqName)
                    containingClass.addSuperTypeListEntryIfNotExists(superClassFqName)
                }
            }
        }
    }

    override fun getText(): String {
        val expression = element ?: return ""
        return when (expression.getReferencedName()) {
            HASH_CODE -> KotlinBundle.message("hash.code.text")
            EQUALS -> KotlinBundle.message("equals.text")
            TO_STRING -> KotlinBundle.message("action.generate.tostring.name")
            else -> {
                val nameToReferTo = getSuperClassFqNameToReferTo(expression) ?: error("isAvailable() was supposed to prevent null")
                KotlinBundle.message("specify.super.type", nameToReferTo.shortName().asString())
            }
        }
    }

    override fun getFamilyName(): String = text
}

private fun prepareToEqualsHashCode(
    clazz: KtClass,
    project: Project,
    block: (KotlinGenerateEqualsAndHashcodeAction, Info) -> KtNamedFunction?
): KtNamedFunction? {
    val action = KotlinGenerateEqualsAndHashcodeAction()
    val membersInfo = action.prepareMembersInfo(clazz, project, false) ?: return null
    return block(action, membersInfo)
}


private fun generateEquals(clazz: KtClass, project: Project): KtNamedFunction? {
    return prepareToEqualsHashCode(clazz, project) { action, info ->
        action.generateEquals(project, info.adjust(needEqualsActually = true), clazz)
    }
}

private fun generateHashCode(clazz: KtClass, project: Project): KtNamedFunction? {
    return prepareToEqualsHashCode(clazz, project) { action, info ->
        action.generateHashCode(project, info.adjust(needHashCodeActually = true), clazz)
    }
}

private fun generateToString(clazz: KtClass, project: Project): KtNamedFunction? {
    val action = KotlinGenerateToStringAction()
    val info = action.prepareMembersInfo(clazz, project, false) ?: return null
    return action.generateToString(clazz, info)
}

private fun Info.adjust(
    needEqualsActually: Boolean = this.needEquals,
    needHashCodeActually: Boolean = this.needHashCode
): Info = Info(
    needEqualsActually, needHashCodeActually, this.classDescriptor, this.variablesForEquals, this.variablesForHashCode
)

private fun KtSuperExpression.specifySuperType(superType: FqName) {
    val label = labelQualifier?.text ?: ""
    val replaced = replaced(KtPsiFactory(this).createExpression("super<${superType.asString()}>$label"))
    ShortenReferences.DEFAULT.process(replaced)
}

private fun KtClassOrObject.addSuperTypeListEntryIfNotExists(superType: FqName) {
    val superTypeFullName = superType.asString()
    val superTypeShortName = superType.shortName().asString()
    val superTypeNames = setOf(superTypeShortName, superTypeFullName)
    val superTypeListEntry = superTypeListEntries.firstOrNull { it.text in superTypeNames }
    if (superTypeListEntry == null) {
        val added = addSuperTypeListEntry(KtPsiFactory(this).createSuperTypeEntry(superTypeFullName))
        ShortenReferences.DEFAULT.process(added)
    } else if (superTypeListEntry.text == superTypeFullName) {
        ShortenReferences.DEFAULT.process(superTypeListEntry)
    }
}

private fun getSuperClassFqNameToReferTo(expression: KtNameReferenceExpression): FqName? {
    fun tryViaCalledFunction(): CallableDescriptor? = expression.resolveToCall()?.resultingDescriptor
        ?.overriddenDescriptors
        ?.find { it.safeAs<SimpleFunctionDescriptor>()?.modality != Modality.ABSTRACT }

    fun tryViaContainingFunction(): CallableDescriptor? = expression.containingFunction()?.descriptor
        ?.safeAs<CallableDescriptor>()?.overriddenDescriptors
        ?.find { it.safeAs<MemberDescriptor>()?.modality != Modality.ABSTRACT }

    val callableToUseInstead = tryViaCalledFunction()
        ?: tryViaContainingFunction()
        ?: return null

    return callableToUseInstead.containingDeclaration.fqNameOrNull()
}

private fun KtNameReferenceExpression.getParentSuperExpression(): KtSuperExpression? =
    parentOfType<KtDotQualifiedExpression>()?.receiverExpression?.safeAs<KtSuperExpression>()
