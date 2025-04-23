// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.types.Variance

object UnfoldPropertyUtils {
    class Context(val propertyExplicitType: String?)

    @OptIn(KaExperimentalApi::class)
    internal fun KaSession.prepareUnfoldPropertyContext(element: KtProperty): Context? {
        val initializer = element.initializer ?: return null

        if (element.typeReference != null) return Context(null)

        val initializerType = initializer.expressionType
        val propertyExplicitType = initializerType?.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)
        return Context(propertyExplicitType)
    }

    /**
     * Initially, the given [property] is in a form of `val foo: Type = initializer`. This function will update it to
     * ```
     * var foo: Type
     * foo = initializer   // assignment
     * ```
     * and return the assignment e.g., `foo = initializer`.
     */
    internal fun splitPropertyDeclaration(property: KtProperty, propertyTypeAsString: String?): KtBinaryExpression? {
        val parent = property.parent
        val initializer = property.initializer ?: return null
        val psiFactory = KtPsiFactory(property.project)
        val expression = psiFactory.createExpressionByPattern("$0 = $1", property.nameAsName!!, initializer)

        val assignment = parent.addAfter(expression, property) as KtBinaryExpression
        parent.addAfter(psiFactory.createNewLine(), property)

        property.initializer = null

        if (propertyTypeAsString != null) {
            val typeReference = psiFactory.createType(propertyTypeAsString)
            property.setTypeReference(typeReference)?.let { ShortenReferencesFacility.getInstance().shorten(it) }
        }
        return assignment
    }
}
