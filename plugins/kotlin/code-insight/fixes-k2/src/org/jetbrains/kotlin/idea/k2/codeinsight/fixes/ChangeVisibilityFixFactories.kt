// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.util.parentsOfType
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeInternal
import org.jetbrains.kotlin.idea.codeinsight.utils.canBePrivate
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeProtected
import org.jetbrains.kotlin.idea.codeinsight.utils.canBePublic
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantSetter
import org.jetbrains.kotlin.idea.codeinsight.utils.toVisibility
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object ChangeVisibilityFixFactories {

    private data class ElementContext(
        val elementName: String,
    )

    private open class ChangeVisibilityModCommandAction(
        element: KtDeclaration,
        elementContext: ElementContext,
        private val forceUsingExplicitModifier: Boolean,
        private val visibilityModifier: KtModifierKeywordToken,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtDeclaration, ElementContext>(element, elementContext) {

        override fun getFamilyName(): String = KotlinBundle.message(
            if (forceUsingExplicitModifier) "make.0.explicitly" else "make.0",
            visibilityModifier,
        )

        override fun getPresentation(
            context: ActionContext,
            element: KtDeclaration,
        ): Presentation {
            val (elementName) = getElementContext(context, element)
            val actionName = KotlinBundle.message(
                if (forceUsingExplicitModifier) "make.0.1.explicitly" else "make.0.1",
                elementName,
                visibilityModifier,
            )
            return Presentation.of(actionName)
        }

        override fun invoke(
            actionContext: ActionContext,
            element: KtDeclaration,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            // TODO: also add logic to change visibility of expect/actual declarations.
            if (visibilityModifier == KtTokens.PUBLIC_KEYWORD
                && !forceUsingExplicitModifier
            ) {
                element.visibilityModifierType()?.let { element.removeModifier(it) }
            } else {
                element.addModifier(visibilityModifier)
            }

            val propertyAccessor = element as? KtPropertyAccessor
            if (propertyAccessor?.isRedundantSetter() == true) {
                removeRedundantSetter(propertyAccessor)
            }
        }
    }

    private class ChangeToPrivateModCommandAction(element: KtDeclaration, elementName: String):
        ChangeVisibilityModCommandAction(element, ElementContext(elementName), false, KtTokens.PRIVATE_KEYWORD), HighPriorityAction

    private class ChangeToInternalModCommandAction(element: KtDeclaration, elementName: String):
        ChangeVisibilityModCommandAction(element, ElementContext(elementName), false, KtTokens.INTERNAL_KEYWORD)

    private class ChangeToProtectedModCommandAction(element: KtDeclaration, elementName: String):
        ChangeVisibilityModCommandAction(element, ElementContext(elementName), false, KtTokens.PROTECTED_KEYWORD)

    private class ChangeToPublicModCommandAction(element: KtDeclaration, elementName: String, forceUsingExplicitModifier: Boolean = true):
        ChangeVisibilityModCommandAction(element, ElementContext(elementName), forceUsingExplicitModifier, KtTokens.PUBLIC_KEYWORD), HighPriorityAction

    val noExplicitVisibilityInApiMode =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoExplicitVisibilityInApiMode ->
            createFixForNoExplicitVisibilityInApiMode(diagnostic.psi)
        }

    val noExplicitVisibilityInApiModeWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoExplicitVisibilityInApiModeWarning ->
            createFixForNoExplicitVisibilityInApiMode(diagnostic.psi)
        }

    val exposedTypealiasExpandedType =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedTypealiasExpandedType ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedFunctionReturnType =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedFunctionReturnType ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedReceiverType =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedReceiverType ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedPropertyType =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedPropertyType ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedPropertyTypeInConstructorError =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedPropertyTypeInConstructorError ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedPropertyTypeInConstructorWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedPropertyTypeInConstructorWarning ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedParameterType =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedParameterType ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedSuperInterface =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedSuperInterface ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedSuperClass =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedSuperClass ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val exposedTypeParameterBound =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExposedTypeParameterBound ->
            createChangeVisibilityFixOnExposure(diagnostic.psi, diagnostic.elementVisibility, diagnostic.restrictingDeclaration, diagnostic.restrictingVisibility)
        }

    val invisibleReference =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InvisibleReference ->
            createChangeVisibilityFixOnInvisibleReference(diagnostic.psi, diagnostic.visible, diagnostic.reference)
        }

    val invisibleSetter =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InvisibleSetter ->
            createChangeVisibilityFixOnInvisibleReference(diagnostic.psi, diagnostic.visibility, diagnostic.property,
                                                          declaration = (diagnostic.property.psi as? KtProperty)?.setter)
        }

    val superCallFromPublicInline =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SuperCallFromPublicInline ->
            createChangeVisibilityFixOnSuperCallFromPublicInline(diagnostic.psi, diagnostic.symbol)
        }

    val nonPublicCallFromPublicInline =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NonPublicCallFromPublicInline ->
            createChangeVisibilityFixOnProtectedCallFromPublicInlineError(
                diagnostic.referencedDeclaration,
                diagnostic.inlineDeclaration
            )
        }

    val protectedCallFromPublicInlineError =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ProtectedCallFromPublicInlineError ->
            createChangeVisibilityFixOnProtectedCallFromPublicInlineError(
                diagnostic.referencedDeclaration,
                diagnostic.inlineDeclaration
            )
        }

    context(KaSession)
    private fun createFixForNoExplicitVisibilityInApiMode(
        element: KtDeclaration,
    ): List<ChangeVisibilityModCommandAction> {
        val elementName = when (element) {
            is KtConstructor<*> -> SpecialNames.INIT.asString()
            is KtNamedDeclaration -> element.name
            else -> null
        } ?: return emptyList()

        return listOf(ChangeToPublicModCommandAction(element, elementName))
    }

    context(KaSession)
    private fun createChangeVisibilityFixOnSuperCallFromPublicInline(
        element: KtElement,
        referencedDeclaration: KaSymbol,
    ): List<ChangeVisibilityModCommandAction> {
        val declaration = (element as? KtExpression)?.containingDeclaration() ?: return emptyList()
        return listOfNotNull(
            createFixToTargetVisibility(referencedDeclaration, declaration, Visibilities.Internal),
            createFixToTargetVisibility(referencedDeclaration, declaration, Visibilities.Private),
        )
    }

    context(KaSession)
    private fun createChangeVisibilityFixOnProtectedCallFromPublicInlineError(
        referencedSymbol: KaSymbol,
        inlineSymbol: KaSymbol
    ): List<ChangeVisibilityModCommandAction> {
        val inlineDeclaration = inlineSymbol.psi as? KtDeclaration ?: return emptyList()
        val referencedDeclaration = referencedSymbol.psi as? KtDeclaration
        return buildList {
            if (referencedDeclaration != null) {
                addIfNotNull(createFixToTargetVisibility(referencedSymbol, referencedDeclaration, Visibilities.Private))
                addIfNotNull(createFixToTargetVisibility(referencedSymbol, referencedDeclaration, Visibilities.Internal))
                val visibilityModifierType = referencedDeclaration.visibilityModifierType() ?: KtTokens.PUBLIC_KEYWORD
                if (inlineDeclaration is KtPropertyAccessor) {
                    addIfNotNull(createFixToTargetVisibility(inlineSymbol, inlineDeclaration.property, visibilityModifierType.toVisibility()))
                } else {
                    addIfNotNull(createFixToTargetVisibility(inlineSymbol, inlineDeclaration, visibilityModifierType.toVisibility()))
                }
                if (visibilityModifierType != KtTokens.PUBLIC_KEYWORD) {
                    addIfNotNull(createFixToTargetVisibility(referencedSymbol, referencedDeclaration, Visibilities.Public))
                }
            }
        }
    }

    private fun KtExpression.containingDeclaration(): KtCallableDeclaration? {
        val declaration = parentOfTypes(KtNamedFunction::class, KtProperty::class)
            ?.safeAs<KtCallableDeclaration>()
            ?.takeIf { it.hasModifier(KtTokens.INLINE_KEYWORD) }
        return declaration?.takeIf { it.name != null }
    }

    context(KaSession)
    private fun createChangeVisibilityFixOnInvisibleReference(
        element: PsiElement,
        visibility: Visibility,
        reference: KaSymbol,
        declaration: KtDeclaration? = reference.psi as? KtDeclaration
    ): List<ChangeVisibilityModCommandAction> {
        if (declaration == null) return emptyList()

        if (declaration.hasModifier(KtTokens.SEALED_KEYWORD) && reference is KaClassSymbol) return emptyList()

        val targetVisibilities = when (visibility) {
            Visibilities.Private, Visibilities.InvisibleFake -> buildList<Visibility> {
                this += Visibilities.Public
                if (declaration.module == element.module) this += Visibilities.Internal
                (element as? KtElement)?.containingClass()?.let { containingClass ->
                    val protectedAllowed = analyze(containingClass) {
                        val classId =
                            (reference as? KaClassSymbol)?.classId ?: ((reference as? KaNamedSymbol)?.containingSymbol as? KaClassSymbol)?.classId ?: return@analyze false
                        (containingClass.symbol as? KaNamedClassSymbol)?.defaultType?.allSupertypes
                            ?.mapNotNull { it.symbol?.classId }
                            ?.filter { it != StandardClassIds.Any }
                            ?.any { classId == it } == true
                    }
                    if (protectedAllowed) this += Visibilities.Protected
                }
            }
            else -> listOf(Visibilities.Public)
        }

        return targetVisibilities.mapNotNull { createFixToTargetVisibility(reference, declaration, it) }
    }

    context(KaSession)
    private fun createChangeVisibilityFixOnExposure(
        element: PsiElement,
        elementVisibility: EffectiveVisibility,
        restrictingSymbol: KaSymbol,
        restrictingVisibility: EffectiveVisibility,
    ): List<ChangeVisibilityModCommandAction> {
        val exposedVisibility = restrictingVisibility.toVisibility()
        val userVisibility = elementVisibility.toVisibility()
        val (targetUserVisibility, targetExposedVisibility) =
            if (exposedVisibility.compareTo(userVisibility)?.let { it < 0 } == true) {
                exposedVisibility to userVisibility
            } else {
                Visibilities.Private to Visibilities.Public
            }

        val userDeclaration = element.parentsOfType<KtDeclaration>(withSelf = true)
            .filterNot { it is KtTypeParameter }
            .filterNot { it is KtParameter && !it.hasValOrVar() }
            .filterNot { it.isPrivate() }
            .firstOrNull()

        val exposedDeclaration = restrictingSymbol.psi
        val protectedAllowed = exposedDeclaration?.parent == userDeclaration?.parent

        val modCommandActions = arrayListOf<ChangeVisibilityModCommandAction>()

        if (userDeclaration != null) {
            if ((restrictingSymbol as? KaDeclarationSymbol)?.isVisible(element) == true) {
                addFixToTargetVisibility(
                    symbol = restrictingSymbol,
                    declaration = userDeclaration,
                    targetVisibility = targetUserVisibility,
                    boundVisibility = Visibilities.Private,
                    protectedAllowed = protectedAllowed,
                    modCommandActions = modCommandActions,
                )
            }
        }

        (restrictingSymbol.psi as? KtDeclaration)?.let {
            addFixToTargetVisibility(
                restrictingSymbol,
                declaration = it,
                targetVisibility = targetExposedVisibility,
                boundVisibility = Visibilities.Public,
                protectedAllowed = protectedAllowed,
                modCommandActions = modCommandActions,
            )
        }

        return modCommandActions
    }

    private fun addFixToTargetVisibility(
        symbol: KaSymbol,
        declaration: KtDeclaration,
        targetVisibility: Visibility,
        boundVisibility: Visibility,
        protectedAllowed: Boolean,
        modCommandActions: MutableList<ChangeVisibilityModCommandAction>
    ) {
        val possibleVisibilities = when (targetVisibility) {
            Visibilities.Protected -> if (protectedAllowed) listOf(boundVisibility, Visibilities.Protected) else listOf(boundVisibility)
            Visibilities.Internal -> listOf(boundVisibility, Visibilities.Internal)
            boundVisibility -> listOf(boundVisibility)
            else -> listOf()
        }

        possibleVisibilities.mapNotNullTo(modCommandActions) {
            createFixToTargetVisibility(symbol, declaration, it)
        }
    }

    private fun createFixToTargetVisibility(
        symbol: KaSymbol,
        declaration: KtDeclaration,
        visibility: Visibility
    ): ChangeVisibilityModCommandAction? {
        val available: Boolean = when (visibility) {
            Visibilities.Private -> declaration.canBePrivate()
            Visibilities.Internal -> declaration.canBeInternal()
            Visibilities.Protected -> declaration.canBeProtected()
            Visibilities.Public -> declaration.canBePublic() && declaration.visibilityModifierType() != KtTokens.DEFAULT_VISIBILITY_KEYWORD
            else -> false
        }

        if (!available) return null
        var name = declaration.asDisplayableName(symbol) ?: return null

        return when (visibility) {
            Visibilities.Private -> ChangeToPrivateModCommandAction(declaration, name)
            Visibilities.Internal -> ChangeToInternalModCommandAction(declaration, name)
            Visibilities.Protected -> ChangeToProtectedModCommandAction(declaration, name)
            Visibilities.Public -> ChangeToPublicModCommandAction(
                declaration,
                name,
                forceUsingExplicitModifier = (declaration as? KtParameter)?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true
            )

            else -> null
        }
    }

    private fun KtDeclaration.asDisplayableName(symbol: KaSymbol): String? {
        return when (this) {
            is KtPrimaryConstructor -> SpecialNames.INIT.asString()
            is KtPropertyAccessor -> symbol.name?.asString()?.let {
                when {
                    isGetter() -> "<get-$it>"
                    isSetter() -> "<set-$it>"
                    else -> null
                }
            }
            else -> name
        }
    }
}

