// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.Variance

internal object RemoveTypeVarianceFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val typeReference = diagnostic.psiElement.parent as? KtTypeReference ?: return null
        val type = typeReference.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, typeReference] ?: return null
        val descriptor = type.constructor.declarationDescriptor as? TypeParameterDescriptor ?: return null
        val variance = descriptor.variance
        if (variance == Variance.INVARIANT) return null
        val typeParameter = DescriptorToSourceUtils.getSourceFromDescriptor(descriptor) as? KtTypeParameter ?: return null
        return RemoveTypeVarianceFix(typeParameter, variance, IdeDescriptorRenderers.SOURCE_CODE_TYPES.renderType(type)).asIntention()
    }
}