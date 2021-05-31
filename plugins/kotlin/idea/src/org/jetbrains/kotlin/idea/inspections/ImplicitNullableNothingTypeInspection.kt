// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.getModalityFromDescriptor
import org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.types.typeUtil.isNullableNothing

class ImplicitNullableNothingTypeInspection : IntentionBasedInspection<KtCallableDeclaration>(
    intention = SpecifyTypeExplicitlyIntention::class,
    additionalChecker = { declaration -> declaration.check() },
    problemText = KotlinBundle.message("implicit.nothing.type")
) {
    override fun inspectionTarget(element: KtCallableDeclaration) = element.nameIdentifier
}

private fun KtCallableDeclaration.check(): Boolean {
    if (!SpecifyTypeExplicitlyIntention.getTypeForDeclaration(this).isNullableNothing()) return false
    val descriptor = this.resolveToDescriptorIfAny()
    return (getModalityFromDescriptor(descriptor) == KtTokens.OPEN_KEYWORD || this is KtProperty && this.isVar) &&
            !isOverridingNullableNothing(descriptor)
}

private fun KtCallableDeclaration.isOverridingNullableNothing(descriptor: DeclarationDescriptor?): Boolean {
    return hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
            (descriptor as? CallableMemberDescriptor)?.overriddenDescriptors?.any { it.returnType?.isNullableNothing() == true } == true
}
