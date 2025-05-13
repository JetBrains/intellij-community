// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pushDown

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.utils.keysToMap

@ApiStatus.Internal
class K1PushDownContext(
    sourceClass: KtClass,
    membersToMove: List<KotlinMemberInfo>,
) : KotlinPushDownContext(sourceClass, membersToMove) {
    val resolutionFacade = sourceClass.getResolutionFacade()

    val sourceClassContext = resolutionFacade.analyzeWithAllCompilerChecks(sourceClass).bindingContext

    val sourceClassDescriptor = sourceClassContext[BindingContext.DECLARATION_TO_DESCRIPTOR, sourceClass] as ClassDescriptor

    val memberDescriptors = membersToMove.map { it.member }.keysToMap {
        when (it) {
            is KtPsiClassWrapper -> it.psiClass.getJavaClassDescriptor(resolutionFacade)!!
            else -> sourceClassContext[BindingContext.DECLARATION_TO_DESCRIPTOR, it]!!
        }
    }
}
