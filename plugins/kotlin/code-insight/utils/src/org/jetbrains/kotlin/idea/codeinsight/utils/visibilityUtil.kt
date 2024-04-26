// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

fun Visibility.toKeywordToken(): KtModifierKeywordToken = when (val normalized = normalize()) {
    Visibilities.Public -> KtTokens.PUBLIC_KEYWORD
    Visibilities.Protected -> KtTokens.PROTECTED_KEYWORD
    Visibilities.Internal -> KtTokens.INTERNAL_KEYWORD
    else -> if (Visibilities.isPrivate(normalized)) KtTokens.PRIVATE_KEYWORD else  error("Unexpected visibility '$normalized'")
}

fun KtModifierKeywordToken.toVisibility(): Visibility = when (this) {
    KtTokens.PUBLIC_KEYWORD -> Visibilities.Public
    KtTokens.PRIVATE_KEYWORD -> Visibilities.Private
    KtTokens.PROTECTED_KEYWORD -> Visibilities.Protected
    KtTokens.INTERNAL_KEYWORD -> Visibilities.Internal
    else -> throw IllegalArgumentException("Unknown visibility modifier '$this'")
}

/**
 * Exclusion list:
 * 1. Primary constructors of public API classes
 * 2. Properties of data classes in public API
 * 3. Overrides of public API. Effectively, this means 'no report on overrides at all'
 * 4. Getters and setters (because getters can't change visibility and setter-only explicit visibility looks ugly)
 * 5. Properties of annotations in public API
 *
 * Do we need something like @PublicApiFile to disable (or invert) this inspection per-file?
 */
context(KtAnalysisSession)
private fun explicitVisibilityRequired(symbol: KtSymbolWithVisibility): Boolean {
    if ((symbol as? KtConstructorSymbol)?.isPrimary == true) return false // 1
    if (symbol is KtPropertySymbol && (symbol.getContainingSymbol() as? KtNamedClassOrObjectSymbol)?.isData == true) return false // 2
    if ((symbol as? KtCallableSymbol)?.getAllOverriddenSymbols()?.isNotEmpty() == true) return false // 3
    if (symbol is KtPropertyAccessorSymbol) return false // 4
    if (symbol is KtPropertySymbol && (symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind == KtClassKind.ANNOTATION_CLASS) return false // 5
    return true
}

fun KtModifierListOwner.setVisibility(visibilityModifier: KtModifierKeywordToken, addImplicitVisibilityModifier: Boolean = false) {
    if (this is KtDeclaration && !addImplicitVisibilityModifier) {
        val defaultVisibilityKeyword = implicitVisibility()
        if (visibilityModifier == defaultVisibilityKeyword) {
            val explicitVisibilityRequired = languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED
                    && analyze(this) { explicitVisibilityRequired(getSymbolOfType<KtSymbolWithVisibility>()) }
            if (!explicitVisibilityRequired) {
                visibilityModifierType()?.let { removeModifier(it) }
                return
            }
        }
    }
    addModifier(visibilityModifier)
}

fun KtDeclaration.implicitVisibility(): KtModifierKeywordToken? {
    return when {
        this is KtPropertyAccessor && isSetter && property.hasModifier(KtTokens.OVERRIDE_KEYWORD) -> {
            analyze(property) {
                property
                    .getSymbolOfType<KtPropertySymbol>()
                    .getAllOverriddenSymbols()
                    .forEach { overriddenSymbol ->
                        val visibility = (overriddenSymbol as? KtPropertySymbol)?.setter?.visibility?.toKeywordToken()
                        if (visibility != null) return visibility
                    }
            }
            KtTokens.DEFAULT_VISIBILITY_KEYWORD
        }

        this is KtConstructor<*> -> {
            // constructors cannot be declared in objects
            val klass = getContainingClassOrObject() as? KtClass ?: return KtTokens.DEFAULT_VISIBILITY_KEYWORD

            when {
                klass.isEnum() -> KtTokens.PRIVATE_KEYWORD
                klass.isSealed() ->
                    if (klass.languageVersionSettings.supportsFeature(LanguageFeature.SealedInterfaces)) KtTokens.PROTECTED_KEYWORD
                    else KtTokens.PRIVATE_KEYWORD

                else -> KtTokens.DEFAULT_VISIBILITY_KEYWORD
            }
        }

        hasModifier(KtTokens.OVERRIDE_KEYWORD) -> {
            analyze(this) {
                getSymbolOfType<KtCallableSymbol>()
                    .getAllOverriddenSymbols()
                    .mapNotNull { (it as? KtSymbolWithVisibility)?.visibility }
                    .maxWithOrNull { v1, v2 -> Visibilities.compare(v1, v2) ?: -1 }
                    ?.toKeywordToken()
            }
        }

        else -> KtTokens.DEFAULT_VISIBILITY_KEYWORD
    }
}

fun KtModifierListOwner.canBePrivate(): Boolean {
    if (modifierList?.hasModifier(KtTokens.ABSTRACT_KEYWORD) == true) return false
    if (this.isAnnotationClassPrimaryConstructor()) return false
    if (this is KtProperty && KotlinPsiHeuristics.hasJvmFieldAnnotation(this)) return false

    if (this is KtDeclaration) {
        if (hasActualModifier() || isExpectDeclaration()) return false
        val containingClassOrObject = containingClassOrObject as? KtClass ?: return true
        if (containingClassOrObject.isAnnotation()) return false
        if (containingClassOrObject.isInterface() && !hasBody()) return false
    }

    return true
}

fun KtModifierListOwner.canBePublic(): Boolean = !isSealedClassConstructor()

fun KtModifierListOwner.canBeProtected(): Boolean {
    return when (val parent = if (this is KtPropertyAccessor) this.property.parent else this.parent) {
        is KtClassBody -> {
            val parentClass = parent.parent as? KtClass
            parentClass != null && !parentClass.isInterface() && !this.isFinalClassConstructor()
        }

        is KtParameterList -> parent.parent is KtPrimaryConstructor
        is KtClass -> !this.isAnnotationClassPrimaryConstructor() && !this.isFinalClassConstructor()
        else -> false
    }
}

fun KtModifierListOwner.canBeInternal(): Boolean {
    if (containingClass()?.isInterface() == true) {
        val objectDeclaration = getStrictParentOfType<KtObjectDeclaration>() ?: return false
        if (objectDeclaration.isCompanion() && KotlinPsiHeuristics.hasJvmFieldAnnotation(this)) return false
    }

    return !isAnnotationClassPrimaryConstructor() && !isSealedClassConstructor()
}

private fun KtModifierListOwner.isAnnotationClassPrimaryConstructor(): Boolean =
    this is KtPrimaryConstructor && (this.parent as? KtClass)?.hasModifier(KtTokens.ANNOTATION_KEYWORD) ?: false

private fun KtModifierListOwner.isFinalClassConstructor(): Boolean {
    if (this !is KtConstructor<*>) return false
    val ktClass = getContainingClassOrObject() as? KtClass ?: return false
    return ktClass.modalityModifierType()?.equals(KtTokens.FINAL_KEYWORD) ?: true
}

private fun KtModifierListOwner.isSealedClassConstructor(): Boolean {
    if (this !is KtConstructor<*>) return false
    val ktClass = getContainingClassOrObject() as? KtClass ?: return false
    return ktClass.isSealed()
}