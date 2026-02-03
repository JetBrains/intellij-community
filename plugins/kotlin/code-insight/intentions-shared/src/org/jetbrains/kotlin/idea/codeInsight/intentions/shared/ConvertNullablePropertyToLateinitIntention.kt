// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class ConvertNullablePropertyToLateinitIntention : KotlinApplicableModCommandAction<KtProperty, Unit>(
    KtProperty::class
) {
    override fun getFamilyName(): String = KotlinBundle.message("convert.to.lateinit.var")

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (element.hasModifier(KtTokens.LATEINIT_KEYWORD)) return false
        if (element.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return false
        if (!element.isVar) return false
        if (element.receiverTypeReference != null) return false
        if (element.hasDelegate()) return false

        val languageVersionSettings = element.languageVersionSettings
        if (!languageVersionSettings.supportsFeature(LanguageFeature.LateinitLocalVariables) && element.isLocal) return false
        if (!languageVersionSettings.supportsFeature(LanguageFeature.LateinitTopLevelProperties) && element.isTopLevel) return false
        if (element.getter != null || element.setter != null) return false
        if (!element.initializer.isNullExpression()) return false

        val typeReference = element.typeReference
        return typeReference?.typeElement is KtNullableType
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val typeReference = element.typeReference ?: return
        val ktNullableType = typeReference.typeElement as? KtNullableType ?: return
        val innerType = ktNullableType.innerType ?: return

        val newTypeReference = KtPsiFactory(element.project).createType(innerType.text)
        typeReference.replace(newTypeReference)
        element.addModifier(KtTokens.LATEINIT_KEYWORD)
        element.initializer = null
    }

    override fun KaSession.prepareContext(element: KtProperty): Unit? {
        val returnType = element.symbol.returnType

        if (!element.isLocal) {
            val propertySymbol = element.symbol as? KaPropertySymbol ?: return null
            if (propertySymbol.backingFieldSymbol == null || returnType.hasFlexibleNullability) return null
        }

        val nonNullableType = returnType.withNullability(false)
        if (nonNullableType is KaTypeParameterType || nonNullableType.isPrimitive) return null

        val classifier = nonNullableType.expandedSymbol
        if (classifier is KaNamedClassSymbol && classifier.isInline) return null

        return Unit
    }

}