// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.pushDown

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.refactoring.pullUp.renderForConflicts
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.idea.util.orEmpty
import org.jetbrains.kotlin.idea.util.toSubstitutor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.scopes.receivers.Qualifier
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.util.findCallableMemberBySignature

fun analyzePushDownConflicts(
    context: K1PushDownContext,
    usages: Array<out UsageInfo>
): MultiMap<PsiElement, String> {
    val targetClasses = usages.mapNotNull { it.element?.unwrapped }

    val conflicts = MultiMap<PsiElement, String>()

    val membersToPush = ArrayList<KtNamedDeclaration>()
    val membersToKeepAbstract = ArrayList<KtNamedDeclaration>()
    for (info in context.membersToMove) {
        val member = info.member
        if (!info.isChecked || ((member is KtClassOrObject || member is KtPsiClassWrapper) && info.overrides != null)) continue

        membersToPush += member
        if ((member is KtNamedFunction || member is KtProperty) && info.isToAbstract && (context.memberDescriptors[member] as CallableMemberDescriptor).modality != Modality.ABSTRACT) {
            membersToKeepAbstract += member
        }
    }

    for (targetClass in targetClasses) {
        checkConflicts(conflicts, context, targetClass, membersToKeepAbstract, membersToPush)
    }

    return conflicts
}

private fun checkConflicts(
    conflicts: MultiMap<PsiElement, String>,
    context: K1PushDownContext,
    targetClass: PsiElement,
    membersToKeepAbstract: List<KtNamedDeclaration>,
    membersToPush: ArrayList<KtNamedDeclaration>
) {
    if (targetClass !is KtClassOrObject) {
        conflicts.putValue(
            targetClass,
            KotlinBundle.message(
                "text.non.kotlin.0.will.not.be.affected.by.refactoring",
                RefactoringUIUtil.getDescription(targetClass, false)
            )
        )
        return
    }

    val targetClassDescriptor = context.resolutionFacade.resolveToDescriptor(targetClass) as ClassDescriptor
    val sourceClassType = context.sourceClassDescriptor.defaultType
    val substitutor = getTypeSubstitution(sourceClassType, targetClassDescriptor.defaultType)?.toSubstitutor().orEmpty()

    if (!context.sourceClass.isInterface() && targetClass is KtClass && targetClass.isInterface()) {
        val message = KotlinBundle.message(
            "text.0.inherits.from.1.it.will.not.be.affected.by.refactoring",
            targetClassDescriptor.renderForConflicts(),
            context.sourceClassDescriptor.renderForConflicts()
        )
        conflicts.putValue(targetClass, message.capitalize())
    }

    for (member in membersToPush) {
        checkMemberClashing(conflicts, context, member, membersToKeepAbstract, substitutor, targetClass, targetClassDescriptor)
        checkSuperCalls(conflicts, context, member, membersToPush)
        checkExternalUsages(conflicts, member, targetClassDescriptor, context.resolutionFacade)
        checkVisibility(conflicts, context, member, targetClassDescriptor)
    }
}

private fun checkMemberClashing(
    conflicts: MultiMap<PsiElement, String>,
    context: K1PushDownContext,
    member: KtNamedDeclaration,
    membersToKeepAbstract: List<KtNamedDeclaration>,
    substitutor: TypeSubstitutor,
    targetClass: KtClassOrObject,
    targetClassDescriptor: ClassDescriptor
) {
    when (member) {
        is KtNamedFunction, is KtProperty -> {
            val memberDescriptor = context.memberDescriptors[member] as CallableMemberDescriptor
            val clashingDescriptor =
                targetClassDescriptor.findCallableMemberBySignature(memberDescriptor.substitute(substitutor) as CallableMemberDescriptor)
            val clashingDeclaration = clashingDescriptor?.source?.getPsi() as? KtNamedDeclaration
            if (clashingDescriptor != null && clashingDeclaration != null) {
                if (memberDescriptor.modality != Modality.ABSTRACT && member !in membersToKeepAbstract) {
                    val message = KotlinBundle.message(
                        "text.0.already.contains.1",
                        targetClassDescriptor.renderForConflicts(),
                        clashingDescriptor.renderForConflicts()
                    )
                    conflicts.putValue(clashingDeclaration, StringUtil.capitalize(message))
                }
                if (!clashingDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                    val message = KotlinBundle.message(
                        "text.0.in.1.will.override.corresponding.member.of.2.after.refactoring",
                        clashingDescriptor.renderForConflicts(),
                        targetClassDescriptor.renderForConflicts(),
                        context.sourceClassDescriptor.renderForConflicts()
                    )
                    conflicts.putValue(clashingDeclaration, StringUtil.capitalize(message))
                }
            }
        }

        is KtClassOrObject -> {
            targetClass.declarations
                .asSequence()
                .filterIsInstance<KtClassOrObject>()
                .firstOrNull { it.name == member.name }
                ?.let {
                    val message = KotlinBundle.message(
                        "text.0.already.contains.nested.class.1",
                        targetClassDescriptor.renderForConflicts(),
                        CommonRefactoringUtil.htmlEmphasize(member.name ?: "")
                    )
                    conflicts.putValue(it, message.capitalize())
                }
        }
    }
}

private fun checkSuperCalls(
    conflicts: MultiMap<PsiElement, String>,
    context: K1PushDownContext,
    member: KtNamedDeclaration,
    membersToPush: ArrayList<KtNamedDeclaration>
) {
    member.accept(
        object : KtTreeVisitorVoid() {
            override fun visitSuperExpression(expression: KtSuperExpression) {
                val qualifiedExpression = expression.getQualifiedExpressionForReceiver() ?: return
                val refExpr = qualifiedExpression.selectorExpression.getCalleeExpressionIfAny() as? KtSimpleNameExpression ?: return
                for (descriptor in refExpr.mainReference.resolveToDescriptors(context.sourceClassContext)) {
                    val memberDescriptor = descriptor as? CallableMemberDescriptor ?: continue
                    val containingClass = memberDescriptor.containingDeclaration as? ClassDescriptor ?: continue
                    if (!DescriptorUtils.isSubclass(context.sourceClassDescriptor, containingClass)) continue
                    val memberInSource = context.sourceClassDescriptor.findCallableMemberBySignature(memberDescriptor)?.source?.getPsi()
                        ?: continue
                    if (memberInSource !in membersToPush) {
                        conflicts.putValue(
                            qualifiedExpression,
                            KotlinBundle.message("text.pushed.member.will.not.be.available.in.0", qualifiedExpression.text)
                        )
                    }
                }
            }
        }
    )
}

internal fun checkExternalUsages(
    conflicts: MultiMap<PsiElement, String>,
    member: PsiElement,
    targetClassDescriptor: ClassDescriptor,
    resolutionFacade: ResolutionFacade
) {
    for (ref in ReferencesSearch.search(member, member.resolveScope, false).asIterable()) {
        val calleeExpr = ref.element as? KtSimpleNameExpression ?: continue
        val resolvedCall = calleeExpr.getResolvedCall(resolutionFacade.analyze(calleeExpr)) ?: continue
        val callElement = resolvedCall.call.callElement
        val dispatchReceiver = resolvedCall.dispatchReceiver
        if (dispatchReceiver == null || dispatchReceiver is Qualifier) continue
        val receiverClassDescriptor = dispatchReceiver.type.constructor.declarationDescriptor as? ClassDescriptor ?: continue
        if (!DescriptorUtils.isSubclass(receiverClassDescriptor, targetClassDescriptor)) {
            conflicts.putValue(callElement, KotlinBundle.message("text.pushed.member.will.not.be.available.in.0", callElement.text))
        }
    }
}

private fun checkVisibility(
    conflicts: MultiMap<PsiElement, String>,
    context: K1PushDownContext,
    member: KtNamedDeclaration,
    targetClassDescriptor: ClassDescriptor
) {
    fun reportConflictIfAny(targetDescriptor: DeclarationDescriptor) {
        val target = (targetDescriptor as? DeclarationDescriptorWithSource)?.source?.getPsi() ?: return
        if (targetDescriptor is DeclarationDescriptorWithVisibility
            && !DescriptorVisibilityUtils.isVisibleIgnoringReceiver(targetDescriptor, targetClassDescriptor, context.resolutionFacade.languageVersionSettings)
        ) {
            val message = KotlinBundle.message(
                "text.0.uses.1.which.is.not.accessible.from.2",
                context.memberDescriptors.getValue(member).renderForConflicts(),
                targetDescriptor.renderForConflicts(),
                targetClassDescriptor.renderForConflicts()
            )
            conflicts.putValue(target, message.capitalize())
        }
    }

    member.accept(
        object : KtTreeVisitorVoid() {
            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                super.visitReferenceExpression(expression)

                expression.references
                    .flatMap { (it as? KtReference)?.resolveToDescriptors(context.sourceClassContext) ?: emptyList() }
                    .forEach(::reportConflictIfAny)

            }
        }
    )
}
