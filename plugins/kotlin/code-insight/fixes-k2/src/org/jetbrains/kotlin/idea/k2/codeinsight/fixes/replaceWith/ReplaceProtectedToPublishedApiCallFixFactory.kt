// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith

import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererOtherModifiersProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererVisibilityModifierProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal object ReplaceProtectedToPublishedApiCallFixFactory {
    private val String.newName: String
        get() = "access\$$this"

    private val String.newNameQuoted: String
        get() = "`$newName`"

    @OptIn(KaExperimentalApi::class)
    private val signatureRenderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        parameterDefaultValueRenderer = KaParameterDefaultValueRenderer.NO_DEFAULT_VALUE
        propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE
        modifiersRenderer = modifiersRenderer.with {
            otherModifiersProvider = object : KaRendererOtherModifiersProvider {
                override fun getOtherModifiers(analysisSession: KaSession, symbol: KaDeclarationSymbol): List<KtModifierKeywordToken> = emptyList()
            }
            visibilityProvider = object : KaRendererVisibilityModifierProvider {
                override fun getVisibilityModifier(analysisSession: KaSession, symbol: KaDeclarationSymbol): KtModifierKeywordToken? = null
            }
        }
        annotationRenderer = annotationRenderer.with {
            annotationFilter = KaRendererAnnotationsFilter.NONE
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createQuickFix(
        element: KtElement,
        referencedDeclaration: KaSymbol,
    ): ReplaceProtectedToPublishedApiCallFix? {
        if (referencedDeclaration !is KaCallableSymbol) return null
        val containingClass = element.containingClass() ?: return null
        val containingDeclaration = referencedDeclaration.containingDeclaration as? KaClassSymbol ?: return null
        if (containingDeclaration.psi != containingClass) return null

        val isProperty = referencedDeclaration is KaPropertySymbol
        val isVar = referencedDeclaration is KaPropertySymbol && !referencedDeclaration.isVal

        val originalName = referencedDeclaration.name?.asString() ?: return null
        val signature = prettyPrint {
            signatureRenderer.renderDeclaration(this@createQuickFix, referencedDeclaration, this)
        }
        val newSignature =
            if (isProperty) {
                signature.replaceFirst("$originalName:", "${originalName.newNameQuoted}:")
            } else {
                signature.replaceFirst("$originalName(", "${originalName.newNameQuoted}(")
            }

        val valueParameters = (referencedDeclaration as? KaFunctionSymbol)?.valueParameters.orEmpty()
        val paramNames = valueParameters.map { it.name.asString() }
        val newName = Name.identifier(originalName.newName)
        val declarationsWithSameName = containingDeclaration.memberScope.declarations.filter {
            it.name == newName
        }
        val isPublishedMemberAlreadyExists = declarationsWithSameName.filterIsInstance<KaCallableSymbol>().any {
            val memberSignature = prettyPrint {
                signatureRenderer.renderDeclaration(this@createQuickFix, it, this)
            }
            memberSignature == newSignature
        }

        return ReplaceProtectedToPublishedApiCallFix(
            element, originalName, paramNames, newSignature,
            isProperty, isVar, isPublishedMemberAlreadyExists
        )
    }

    val protectedCallFromPublicInline = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ProtectedCallFromPublicInline ->
        val startOffset = diagnostic.textRanges.singleOrNull()?.startOffset ?: return@ModCommandBased emptyList()
        val referenceExpression = diagnostic.psi.containingKtFile.findElementAt(startOffset)?.findParentOfType<KtReferenceExpression>() ?: return@ModCommandBased emptyList()
        listOfNotNull(createQuickFix(referenceExpression, diagnostic.referencedDeclaration))
    }

    val protectedCallFromPublicInlineError =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ProtectedCallFromPublicInlineError ->
            val startOffset = diagnostic.textRanges.singleOrNull()?.startOffset ?: return@ModCommandBased emptyList()
            val referenceExpression = diagnostic.psi.containingKtFile.findElementAt(startOffset)?.findParentOfType<KtReferenceExpression>() ?: return@ModCommandBased emptyList()
            listOfNotNull(createQuickFix(referenceExpression, diagnostic.referencedDeclaration))
        }
}