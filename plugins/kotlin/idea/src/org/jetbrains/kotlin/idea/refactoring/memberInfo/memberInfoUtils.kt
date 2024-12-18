// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.javaResolutionFacade
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

fun PsiNamedElement.getClassDescriptorIfAny(resolutionFacade: ResolutionFacade? = null): ClassDescriptor? {
    return when (this) {
        is KtClassOrObject -> resolutionFacade?.resolveToDescriptor(this) ?: resolveToDescriptorIfAny(BodyResolveMode.FULL)
        is PsiClass -> getJavaClassDescriptor()
        else -> null
    } as? ClassDescriptor
}

// Applies to JetClassOrObject and PsiClass
@JvmName("qualifiedClassNameForRendering") // to preserve binary compatibility with external usages
@Deprecated(
    "Use 'qualifiedClassNameForRendering' instead",
    ReplaceWith(
        "this.qualifiedClassNameForRendering()", 
        "org.jetbrains.kotlin.idea.refactoring.memberInfo.qualifiedClassNameForRendering"
    )
)
@NlsSafe
fun PsiNamedElement.qualifiedClassNameForRenderingOld(): String = qualifiedClassNameForRendering()

fun KotlinMemberInfo.getChildrenToAnalyze(): List<PsiElement> {
    val member = member
    val childrenToCheck = member.allChildren.toMutableList()
    if (isToAbstract && member is KtCallableDeclaration) {
        when (member) {
            is KtNamedFunction -> childrenToCheck.remove(member.bodyExpression as PsiElement?)
            is KtProperty -> {
                childrenToCheck.remove(member.initializer as PsiElement?)
                childrenToCheck.remove(member.delegateExpression as PsiElement?)
                childrenToCheck.removeAll(member.accessors)
            }
        }
    }
    return childrenToCheck
}

internal fun KtNamedDeclaration.resolveToDescriptorWrapperAware(resolutionFacade: ResolutionFacade? = null): DeclarationDescriptor {
    if (this is KtPsiClassWrapper) {
        (resolutionFacade ?: psiClass.javaResolutionFacade())
            ?.let { psiClass.getJavaClassDescriptor(it) }
            ?.let { return it }
    }
    return resolutionFacade?.resolveToDescriptor(this) ?: unsafeResolveToDescriptor()
}

internal fun PsiMember.toKtDeclarationWrapperAware(): KtNamedDeclaration? {
    if (this is PsiClass && this !is KtLightClass) return KtPsiClassWrapper(this)
    return namedUnwrappedElement as? KtNamedDeclaration
}