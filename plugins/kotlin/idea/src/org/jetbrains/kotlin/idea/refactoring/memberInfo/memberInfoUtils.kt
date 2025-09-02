// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.javaResolutionFacade
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
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

internal fun KtNamedDeclaration.resolveToDescriptorWrapperAware(resolutionFacade: ResolutionFacade? = null): DeclarationDescriptor {
    if (this is KtPsiClassWrapper) {
        (resolutionFacade ?: psiClass.javaResolutionFacade())
            ?.let { psiClass.getJavaClassDescriptor(it) }
            ?.let { return it }
    }
    return resolutionFacade?.resolveToDescriptor(this) ?: unsafeResolveToDescriptor()
}
