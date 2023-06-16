// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider.ValidatorTarget
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.util.getAllAccessibleFunctions
import org.jetbrains.kotlin.idea.util.getAllAccessibleVariables
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

open class Fe10KotlinNewDeclarationNameValidator(
    private val visibleDeclarationsContext: KtElement?,
    private val checkDeclarationsIn: Sequence<PsiElement>,
    private val target: ValidatorTarget,
    private val excludedDeclarations: List<KtDeclaration> = emptyList()
) : (String) -> Boolean {
    constructor(
        container: PsiElement,
        anchor: PsiElement?,
        target: ValidatorTarget,
        excludedDeclarations: List<KtDeclaration> = emptyList()
    ) : this(
        (anchor ?: container).parentsWithSelf.firstIsInstanceOrNull<KtElement>(),
        anchor?.siblings() ?: container.allChildren,
        target,
        excludedDeclarations
    )

    override fun invoke(name: String): Boolean {
        val identifier = Name.identifier(name)

        if (visibleDeclarationsContext != null) {
            val bindingContext = visibleDeclarationsContext.analyze(BodyResolveMode.PARTIAL_FOR_COMPLETION)
            val resolutionScope =
                visibleDeclarationsContext.getResolutionScope(bindingContext, visibleDeclarationsContext.getResolutionFacade())
            if (resolutionScope.hasConflict(identifier, visibleDeclarationsContext.getResolutionFacade().languageVersionSettings)) return false
        }

        return checkDeclarationsIn.none {
            it.findDescendantOfType<KtNamedDeclaration> { it.isConflicting(identifier) } != null
        }
    }

    private fun isExcluded(it: DeclarationDescriptorWithSource) = ErrorUtils.isError(it) || it.source.getPsi() in excludedDeclarations

    private fun LexicalScope.hasConflict(name: Name, languageVersionSettings: LanguageVersionSettings): Boolean {
        fun DeclarationDescriptor.isVisible(): Boolean {
            return when (this) {
                is DeclarationDescriptorWithVisibility -> isVisible(ownerDescriptor, languageVersionSettings)
                else -> true
            }
        }

        return when (target) {
            ValidatorTarget.PROPERTY, ValidatorTarget.PARAMETER, ValidatorTarget.VARIABLE -> {
                getAllAccessibleVariables(name)
                    .any { !it.isExtension && it.isVisible() && !isExcluded(it) }
            }
            ValidatorTarget.CLASS, ValidatorTarget.FUNCTION -> {
                getAllAccessibleFunctions(name)
                    .any { !it.isExtension && it.isVisible() && !isExcluded(it) }
                        || findClassifier(name, NoLookupLocation.FROM_IDE)?.let { it.isVisible() && !isExcluded(it) } ?: false
            }
        }
    }

    private fun KtNamedDeclaration.isConflicting(name: Name): Boolean {
        if (this in excludedDeclarations) return false
        if (nameAsName != name) return false
        if (this is KtCallableDeclaration && receiverTypeReference != null) return false

        return when (target) {
            ValidatorTarget.PROPERTY, ValidatorTarget.PARAMETER, ValidatorTarget.VARIABLE ->
                this is KtVariableDeclaration || this is KtParameter
            ValidatorTarget.CLASS, ValidatorTarget.FUNCTION ->
                this is KtNamedFunction || this is KtClassOrObject || this is KtTypeAlias
        }
    }
}