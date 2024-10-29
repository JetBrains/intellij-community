// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError

internal object ChangeAccessorTypeFixFactory : KotlinSingleIntentionActionFactory() {
    public override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val propertyAccessor = diagnostic.psiElement.getParentOfType<KtPropertyAccessor>(
            strict = false,
            KtProperty::class.java,
        ) ?: return null

        val type = propertyAccessor.property.resolveToDescriptorIfAny()?.type?.takeUnless(KotlinType::isError) ?: return null
        val typePresentation = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(type)
        val typeSourceCode = IdeDescriptorRenderers.SOURCE_CODE.renderType(type)

        return ChangeAccessorTypeFix(
            propertyAccessor,
            typePresentation,
            typeSourceCode,
        ).asIntention()
    }
}
