// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities.*
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory3
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

object ChangeVisibilityOnExposureFactory : KotlinIntentionActionsFactory() {

    private fun addFixToTargetVisibility(
        modifierListOwner: KtModifierListOwner,
        descriptor: DeclarationDescriptorWithVisibility,
        targetVisibility: DescriptorVisibility,
        boundVisibility: DescriptorVisibility,
        protectedAllowed: Boolean,
        fixes: MutableList<IntentionAction>,
    ) {
        val possibleVisibilities = when (targetVisibility) {
            PROTECTED -> if (protectedAllowed) listOf(boundVisibility, PROTECTED) else listOf(boundVisibility)
            INTERNAL -> listOf(boundVisibility, INTERNAL)
            boundVisibility -> listOf(boundVisibility)
            else -> listOf()
        }

        possibleVisibilities.mapNotNullTo(fixes) { ChangeVisibilityFix.create(modifierListOwner, descriptor, it) }
    }

    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        @Suppress("UNCHECKED_CAST")
        val factory = diagnostic.factory as DiagnosticFactory3<*, EffectiveVisibility, DescriptorWithRelation, EffectiveVisibility>
        // We have USER that uses some EXPOSED object. USER visibility must be same or less permissive.
        val exposedDiagnostic = factory.cast(diagnostic)
        val exposedDescriptor = exposedDiagnostic.b.descriptor as? DeclarationDescriptorWithVisibility
            ?: return emptyList()

        val exposedDeclaration = DescriptorToSourceUtils.getSourceFromDescriptor(exposedDescriptor) as? KtModifierListOwner
            ?: return emptyList()

        val exposedVisibility = exposedDiagnostic.c.toDescriptorVisibility()
        val userVisibility = exposedDiagnostic.a.toDescriptorVisibility()
        val (targetUserVisibility, targetExposedVisibility) =
            if (exposedVisibility.compareTo(userVisibility)?.let { it < 0 } == true)
                Pair(exposedVisibility, userVisibility)
            else
                Pair(PRIVATE, PUBLIC)

        val result = ArrayList<IntentionAction>()
        val userDeclaration = diagnostic.psiElement.getParentOfType<KtDeclaration>(true)
        val protectedAllowed = exposedDeclaration.parent == userDeclaration?.parent
        if (userDeclaration != null && !userDeclaration.isPrivate()) {
            val userDescriptor = userDeclaration.toDescriptor() as? DeclarationDescriptorWithVisibility
            if (userDescriptor != null && DescriptorVisibilityUtils.isVisibleIgnoringReceiver(
                exposedDescriptor,
                userDescriptor,
                exposedDeclaration.getResolutionFacade().languageVersionSettings,
                )
            ) {
                addFixToTargetVisibility(
                    modifierListOwner = userDeclaration,
                    descriptor = userDescriptor,
                    targetVisibility = targetUserVisibility,
                    boundVisibility = PRIVATE,
                    protectedAllowed = protectedAllowed,
                    fixes = result,
                )
            }
        }

        addFixToTargetVisibility(
            modifierListOwner = exposedDeclaration,
            descriptor = exposedDescriptor,
            targetVisibility = targetExposedVisibility,
            boundVisibility = PUBLIC,
            protectedAllowed = protectedAllowed,
            fixes = result,
        )

        return result
    }
}
