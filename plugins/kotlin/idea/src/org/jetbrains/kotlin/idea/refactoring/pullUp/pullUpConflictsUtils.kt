// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.getChildrenToAnalyze
import org.jetbrains.kotlin.idea.refactoring.memberInfo.resolveToDescriptorWrapperAware
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.findCallableMemberBySignature

fun checkConflicts(
    project: Project,
    sourceClass: KtClassOrObject,
    targetClass: PsiNamedElement,
    memberInfos: List<KotlinMemberInfo>,
    onShowConflicts: () -> Unit = {},
    onAccept: () -> Unit
) {
    val conflicts = MultiMap<PsiElement, String>()

    val conflictsCollected = runProcessWithProgressSynchronously(RefactoringBundle.message("detecting.possible.conflicts"), project) {
        runReadAction { collectConflicts(sourceClass, targetClass, memberInfos, conflicts) }
    }

    if (conflictsCollected) {
        project.checkConflictsInteractively(conflicts, onShowConflicts, onAccept)
    } else {
        onShowConflicts()
    }
}

private fun runProcessWithProgressSynchronously(
    progressTitle: @NlsContexts.ProgressTitle String,
    project: Project?,
    process: Runnable,
): Boolean = ProgressManager.getInstance().runProcessWithProgressSynchronously(process, progressTitle, true, project)

private fun collectConflicts(
    sourceClass: KtClassOrObject,
    targetClass: PsiNamedElement,
    memberInfos: List<KotlinMemberInfo>,
    conflicts: MultiMap<PsiElement, String>
) {
    val pullUpData = KotlinPullUpData(sourceClass,
                                      targetClass,
                                       memberInfos.mapNotNull { it.member })

    with(pullUpData) {
        for (memberInfo in memberInfos) {
            val member = memberInfo.member
            val memberDescriptor = member.resolveToDescriptorWrapperAware(resolutionFacade)

            checkClashWithSuperDeclaration(member, memberDescriptor, conflicts)
            checkAccidentalOverrides(member, memberDescriptor, conflicts)
            checkInnerClassToInterface(member, memberDescriptor, conflicts)
            checkVisibility(memberInfo, memberDescriptor, conflicts, resolutionFacade.languageVersionSettings)
        }
    }
    checkVisibilityInAbstractedMembers(memberInfos, pullUpData.resolutionFacade, conflicts)
}

internal fun checkVisibilityInAbstractedMembers(
    memberInfos: List<KotlinMemberInfo>,
    resolutionFacade: ResolutionFacade,
    conflicts: MultiMap<PsiElement, String>
) {
    val membersToMove = ArrayList<KtNamedDeclaration>()
    val membersToAbstract = ArrayList<KtNamedDeclaration>()

    for (memberInfo in memberInfos) {
        val member = memberInfo.member ?: continue
        (if (memberInfo.isToAbstract) membersToAbstract else membersToMove).add(member)
    }

    for (member in membersToAbstract) {
        val memberDescriptor = member.resolveToDescriptorWrapperAware(resolutionFacade)
        member.forEachDescendantOfType<KtSimpleNameExpression> {
            val target = it.mainReference.resolve() as? KtNamedDeclaration ?: return@forEachDescendantOfType
            if (!willBeMoved(target, membersToMove)) return@forEachDescendantOfType
            if (target.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                val targetDescriptor = target.resolveToDescriptorWrapperAware(resolutionFacade)
                val memberText = memberDescriptor.renderForConflicts()
                val targetText = targetDescriptor.renderForConflicts()
                val message = KotlinBundle.message("text.0.uses.1.which.will.not.be.accessible.from.subclass", memberText, targetText)
                conflicts.putValue(target, message.capitalize())
            }
        }
    }
}

internal fun willBeMoved(element: PsiElement, membersToMove: Collection<KtNamedDeclaration>) =
    element.parentsWithSelf.any { it in membersToMove }

internal fun willBeUsedInSourceClass(
    member: PsiElement,
    sourceClass: KtClassOrObject,
    membersToMove: Collection<KtNamedDeclaration>
): Boolean {
    return !ReferencesSearch
        .search(member, LocalSearchScope(sourceClass), false)
        .all { willBeMoved(it.element, membersToMove) }
}

private val CALLABLE_RENDERER = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.withOptions {
    parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
    modifiers = emptySet()
    startFromName = false
}

@Nls
fun DeclarationDescriptor.renderForConflicts(): String {
    return when (this) {
        is ClassDescriptor -> {
            @NlsSafe val text = "${DescriptorRenderer.getClassifierKindPrefix(this)} " +
                    IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(this)
            text
        }
        is FunctionDescriptor -> {
            KotlinBundle.message("text.function.in.ticks.0", CALLABLE_RENDERER.render(this))
        }
        is PropertyDescriptor -> {
            KotlinBundle.message("text.property.in.ticks.0", CALLABLE_RENDERER.render(this))
        }
        is PackageFragmentDescriptor -> {
            @NlsSafe val text = fqName.asString()
            text
        }
        is PackageViewDescriptor -> {
            @NlsSafe val text = fqName.asString()
            text
        }
        else -> {
            ""
        }
    }
}

internal fun KotlinPullUpData.getClashingMemberInTargetClass(memberDescriptor: CallableMemberDescriptor): CallableMemberDescriptor? {
    val memberInSuper = memberDescriptor.substitute(sourceToTargetClassSubstitutor) ?: return null
    return targetClassDescriptor.findCallableMemberBySignature(memberInSuper as CallableMemberDescriptor)
}

private fun KotlinPullUpData.checkClashWithSuperDeclaration(
    member: KtNamedDeclaration,
    memberDescriptor: DeclarationDescriptor,
    conflicts: MultiMap<PsiElement, String>
) {
    val message = KotlinBundle.message(
        "text.class.0.already.contains.member.1",
        targetClassDescriptor.renderForConflicts(),
        memberDescriptor.renderForConflicts()
    )

    if (member is KtParameter) {
        if (((targetClass as? KtClass)?.primaryConstructorParameters ?: emptyList()).any { it.name == member.name }) {
            conflicts.putValue(member, message.capitalize())
        }
        return
    }

    if (memberDescriptor !is CallableMemberDescriptor) return

    val clashingSuper = getClashingMemberInTargetClass(memberDescriptor) ?: return
    if (clashingSuper.modality == Modality.ABSTRACT) return
    if (clashingSuper.kind != CallableMemberDescriptor.Kind.DECLARATION) return
    conflicts.putValue(member, message.capitalize())
}

private fun PsiClass.isSourceOrTarget(data: KotlinPullUpData): Boolean {
    var element = unwrapped
    if (element is KtObjectDeclaration && element.isCompanion()) element = element.containingClassOrObject

    return element == data.sourceClass || element == data.targetClass
}

private fun KotlinPullUpData.checkAccidentalOverrides(
    member: KtNamedDeclaration,
    memberDescriptor: DeclarationDescriptor,
    conflicts: MultiMap<PsiElement, String>
) {
    if (memberDescriptor is CallableDescriptor && !member.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
        val memberDescriptorInTargetClass = memberDescriptor.substitute(sourceToTargetClassSubstitutor)
        if (memberDescriptorInTargetClass != null) {
            val sequence = HierarchySearchRequest<PsiElement>(targetClass, targetClass.useScope())
                .searchInheritors()
                .asSequence()
                .filterNot { it.isSourceOrTarget(this) }
                .mapNotNull { it.unwrapped as? KtClassOrObject }

            for (it in sequence) {
                val subClassDescriptor = it.resolveToDescriptorWrapperAware(resolutionFacade) as ClassDescriptor
                val substitution = getTypeSubstitution(targetClassDescriptor.defaultType, subClassDescriptor.defaultType).orEmpty()

                val memberDescriptorInSubClass = memberDescriptorInTargetClass.substitute(substitution) as? CallableMemberDescriptor
                val clashingMemberDescriptor = memberDescriptorInSubClass?.let {
                    subClassDescriptor.findCallableMemberBySignature(it)
                } ?: continue

                val clashingMember = clashingMemberDescriptor.source.getPsi() ?: continue
                val message = KotlinBundle.message(
                    "text.member.0.in.super.class.will.clash.with.existing.member.of.1",
                    memberDescriptor.renderForConflicts(),
                    it.resolveToDescriptorWrapperAware(resolutionFacade).renderForConflicts()
                )

                conflicts.putValue(clashingMember, message.capitalize())
            }
        }
    }
}

private fun KotlinPullUpData.checkInnerClassToInterface(
    member: KtNamedDeclaration,
    memberDescriptor: DeclarationDescriptor,
    conflicts: MultiMap<PsiElement, String>
) {
    if (isInterfaceTarget && memberDescriptor is ClassDescriptor && memberDescriptor.isInner) {
        val message = KotlinBundle.message("text.inner.class.0.cannot.be.moved.to.interface", memberDescriptor.renderForConflicts())
        conflicts.putValue(member, message.capitalize())
    }
}

private fun KotlinPullUpData.checkVisibility(
    memberInfo: KotlinMemberInfo,
    memberDescriptor: DeclarationDescriptor,
    conflicts: MultiMap<PsiElement, String>,
    languageVersionSettings: LanguageVersionSettings
) {
    fun reportConflictIfAny(targetDescriptor: DeclarationDescriptor, languageVersionSettings: LanguageVersionSettings) {
        if (targetDescriptor in memberDescriptors.values) return
        val target = (targetDescriptor as? DeclarationDescriptorWithSource)?.source?.getPsi() ?: return
        if (targetDescriptor is DeclarationDescriptorWithVisibility
            && !DescriptorVisibilityUtils.isVisibleIgnoringReceiver(targetDescriptor, targetClassDescriptor, languageVersionSettings)
        ) {
            val message = RefactoringBundle.message(
                "0.uses.1.which.is.not.accessible.from.the.superclass",
                memberDescriptor.renderForConflicts(),
                targetDescriptor.renderForConflicts()
            )
            conflicts.putValue(target, message.capitalize())
        }
    }

    val member = memberInfo.member
    val childrenToCheck = memberInfo.getChildrenToAnalyze()
    if (memberInfo.isToAbstract && member is KtCallableDeclaration) {
        if (member.typeReference == null) {
            (memberDescriptor as CallableDescriptor).returnType?.let { returnType ->
                val typeInTargetClass = sourceToTargetClassSubstitutor.substitute(returnType, Variance.INVARIANT)
                val descriptorToCheck = typeInTargetClass?.constructor?.declarationDescriptor as? ClassDescriptor
                if (descriptorToCheck != null) {
                    reportConflictIfAny(descriptorToCheck, languageVersionSettings)
                }
            }
        }
    }

    childrenToCheck.forEach { children ->
        children.accept(
            object : KtTreeVisitorVoid() {
                override fun visitReferenceExpression(expression: KtReferenceExpression) {
                    super.visitReferenceExpression(expression)

                    val context = resolutionFacade.analyze(expression)
                    expression.references
                        .flatMap { (it as? KtReference)?.resolveToDescriptors(context) ?: emptyList() }
                        .forEach { reportConflictIfAny(it, languageVersionSettings) }
                }
            }
        )
    }
}
