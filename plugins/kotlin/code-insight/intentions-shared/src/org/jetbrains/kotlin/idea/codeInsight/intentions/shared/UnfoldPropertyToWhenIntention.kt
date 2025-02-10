// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.BranchedUnfoldingUtils
import org.jetbrains.kotlin.psi.*

class UnfoldPropertyToWhenIntention: KotlinApplicableModCommandAction<KtProperty, UnfoldPropertyToWhenIntention.Context>(KtProperty::class) {
    class Context(val propertyExplicitType: String?)

    override fun invoke(
      actionContext: ActionContext,
      property: KtProperty,
      elementContext: Context,
      updater: ModPsiUpdater
    ) {
        val assignment = splitPropertyDeclaration(property, elementContext.propertyExplicitType) ?: return
        BranchedUnfoldingUtils.unfoldAssignmentToWhen(assignment) { updater.moveCaretTo(it) }
    }

    /**
     * Initially, the given [property] is in a form of `val foo: Type = initializer`. This function will update it to
     * ```
     * var foo: Type
     * foo = initializer   // assignment
     * ```
     * and return the assignment e.g., `foo = initializer`.
     */
    private fun splitPropertyDeclaration(property: KtProperty, propertyTypeAsString: String?): KtBinaryExpression? {
        val parent = property.parent
        val initializer = property.initializer ?: return null
        val psiFactory = KtPsiFactory(property.project)
        val assignment = psiFactory.createExpressionByPattern("$0 = $1", property.nameAsName!!, initializer)

        val addedAssignment = parent.addAfter(assignment, property) as KtBinaryExpression
        parent.addAfter(psiFactory.createNewLine(), property)

        property.initializer = null

        if (propertyTypeAsString != null) {
            val typeReference = psiFactory.createType(propertyTypeAsString)
            property.setTypeReference(typeReference)?.let { ShortenReferencesFacility.getInstance().shorten(it) }
        }
        return addedAssignment
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.property.initializer.with.when.expression")

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (!element.isLocal) return false
        val initializer = element.initializer as? KtWhenExpression ?: return false
        if (!KtPsiUtil.checkWhenExpressionHasSingleElse(initializer)) return false
        return initializer.entries.none { it.expression == null }
    }

    override fun KaSession.prepareContext(element: KtProperty): Context? {
        val initializer = element.initializer ?: return null

        if (element.typeReference != null) return Context(null)

        return analyze(initializer) {
          val initializerType = initializer.expressionType ?: return@analyze Context(null)
          val typeAsString = initializerType.expandedSymbol?.classId?.asFqNameString() ?: return@analyze Context(null)
          if (initializerType.isMarkedNullable) Context("$typeAsString?") else Context(typeAsString)
        }
    }
}