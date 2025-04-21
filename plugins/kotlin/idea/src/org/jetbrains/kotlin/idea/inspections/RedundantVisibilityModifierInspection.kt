// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.implicitVisibility
import org.jetbrains.kotlin.idea.inspections.RedundantVisibilityModifierInspection.Holder.getRedundantVisibility
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.declarationVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import org.jetbrains.kotlin.resolve.descriptorUtil.isEffectivelyPublicApi

class RedundantVisibilityModifierInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
        declarationVisitor(fun(declaration: KtDeclaration) {
            val visibilityModifier = declaration.visibilityModifier() ?: return
            val redundantVisibility = getRedundantVisibility(declaration, skipGetters = true) ?: return
            holder.registerProblem(
                visibilityModifier,
                KotlinBundle.message("redundant.visibility.modifier"),
                IntentionWrapper(RemoveModifierFixBase(declaration, redundantVisibility, isRedundant = true))
            )
        })

    object Holder {
        fun getRedundantVisibility(declaration: KtDeclaration, skipGetters: Boolean = false): KtModifierKeywordToken? {
            if (skipGetters && declaration is KtPropertyAccessor && declaration.isGetter) {
                // There is a quick fix for REDUNDANT_MODIFIER_IN_GETTER
                return null
            }

            val visibilityModifier = declaration.visibilityModifier() ?: return null

            if (isVisibilityNeededForExplicitApiMode(declaration)) return null

            val implicitVisibility = declaration.implicitVisibility()
            val redundantVisibility = when {
                visibilityModifier.node.elementType == implicitVisibility -> implicitVisibility
                declaration.hasModifier(INTERNAL_KEYWORD) && declaration.isInsideLocalOrPrivate() -> INTERNAL_KEYWORD
                else -> null
            } ?: return null

            if (redundantVisibility == PUBLIC_KEYWORD
                && declaration is KtProperty
                && declaration.hasModifier(OVERRIDE_KEYWORD)
                && declaration.isVar
                && declaration.setterVisibility().let { it != null && it != DescriptorVisibilities.PUBLIC }
            ) return null

            return redundantVisibility
        }

        private fun KtDeclaration.isInsideLocalOrPrivate(): Boolean =
            containingClassOrObject?.let { it.isLocal || it.isPrivate() } == true

        private fun isVisibilityNeededForExplicitApiMode(declaration: KtDeclaration): Boolean {
            val isExplicitApiMode = declaration.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED
            if (!isExplicitApiMode) return false
            return (declaration.resolveToDescriptorIfAny() as? DeclarationDescriptorWithVisibility)?.isEffectivelyPublicApi == true
        }

        private fun KtProperty.setterVisibility(): DescriptorVisibility? {
            val descriptor = descriptor as? PropertyDescriptor ?: return null
            if (setter?.visibilityModifier() != null) {
                val visibility = descriptor.setter?.visibility
                if (visibility != null) return visibility
            }
            return (descriptor as? CallableMemberDescriptor)
                ?.overriddenDescriptors
                ?.firstNotNullOfOrNull { (it as? PropertyDescriptor)?.setter }
                ?.visibility
        }
    }
}
