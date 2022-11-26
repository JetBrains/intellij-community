// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy

private val FUNCTION_RENDERER = DescriptorRenderer.withOptions {
    withDefinedIn = false
    modifiers = emptySet()
    classifierNamePolicy = ClassifierNamePolicy.SHORT
    withoutTypeParameters = true
    parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
}

fun wrapOrSkip(s: String, inCode: Boolean) = if (inCode) "<code>$s</code>" else s

fun formatClassDescriptor(classDescriptor: DeclarationDescriptor) =
    IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.render(classDescriptor)

fun formatPsiClass(
    psiClass: PsiClass,
    markAsJava: Boolean,
    inCode: Boolean
): String {
    var description: String

    val kind = if (psiClass.isInterface) "interface " else "class "
    description = kind + PsiFormatUtil.formatClass(
        psiClass,
        PsiFormatUtilBase.SHOW_CONTAINING_CLASS or PsiFormatUtilBase.SHOW_NAME or PsiFormatUtilBase.SHOW_PARAMETERS or PsiFormatUtilBase.SHOW_TYPE
    )
    description = wrapOrSkip(description, inCode)

    return if (markAsJava) "[Java] $description" else description
}


private fun formatFunctionDescriptor(functionDescriptor: DeclarationDescriptor): String = FUNCTION_RENDERER.render(functionDescriptor)

fun formatPsiMethod(
    psiMethod: PsiMethod,
    showContainingClass: Boolean,
    inCode: Boolean
): String {
    var options = PsiFormatUtilBase.SHOW_NAME or PsiFormatUtilBase.SHOW_PARAMETERS or PsiFormatUtilBase.SHOW_TYPE
    if (showContainingClass) {
        //noinspection ConstantConditions
        options = options or PsiFormatUtilBase.SHOW_CONTAINING_CLASS
    }

    var description = PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, options, PsiFormatUtilBase.SHOW_TYPE)
    description = wrapOrSkip(description, inCode)

    return "[Java] $description"
}

fun formatJavaOrLightMethod(method: PsiMethod): String {
    val originalDeclaration = method.unwrapped
    return if (originalDeclaration is KtDeclaration) {
        formatFunctionDescriptor(originalDeclaration.unsafeResolveToDescriptor())
    } else {
        formatPsiMethod(method, showContainingClass = false, inCode = false)
    }
}

fun formatClass(classOrObject: KtClassOrObject) = formatClassDescriptor(classOrObject.unsafeResolveToDescriptor() as ClassDescriptor)