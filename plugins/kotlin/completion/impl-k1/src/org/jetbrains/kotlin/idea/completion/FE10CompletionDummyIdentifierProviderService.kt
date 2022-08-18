// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.completion.implCommon.AbstractCompletionDummyIdentifierProviderService
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class FE10CompletionDummyIdentifierProviderService: AbstractCompletionDummyIdentifierProviderService() {
    override fun allTargetsAreFunctionsOrClasses(nameReferenceExpression: KtNameReferenceExpression): Boolean {
        val bindingContext = nameReferenceExpression.getResolutionFacade().analyze(nameReferenceExpression, BodyResolveMode.PARTIAL)
        val targets = nameReferenceExpression.getReferenceTargets(bindingContext)
        return targets.isNotEmpty() && targets.all { it is FunctionDescriptor || it is ClassDescriptor && it.kind == ClassKind.CLASS }
    }
}