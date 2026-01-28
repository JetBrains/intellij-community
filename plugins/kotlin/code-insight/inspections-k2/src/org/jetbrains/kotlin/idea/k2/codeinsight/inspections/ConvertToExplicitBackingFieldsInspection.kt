// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

/**
 * This is an experimental feature and has to be explicitly turned on. Then this inspection will be enabled by default.
 * See [feature discussion](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0430-explicit-backing-fields.md) for more details.
 */
internal class ConvertToExplicitBackingFieldsInspection :
    KotlinApplicableInspectionBase.Simple<KtProperty, ConvertToExplicitBackingFieldsInspection.Context>() {

    data class Context(val backingProperty: KtProperty)

    override fun getProblemDescription(
        element: KtProperty,
        context: Context
    ): @InspectionMessage String = KotlinBundle.message("inspection.kotlin.convert.to.explicit.backing.fields.display.name")

    override fun isAvailableForFile(file: PsiFile): Boolean {
        return file is KtFile && file.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitBackingFields)
    }

    override fun createQuickFix(
        element: KtProperty,
        context: Context
    ): KotlinModCommandQuickFix<KtProperty> = object : KotlinModCommandQuickFix<KtProperty>() {
        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.kotlin.convert.to.explicit.backing.fields.fix.name")

        override fun applyFix(project: Project, element: KtProperty, updater: ModPsiUpdater) {
            val psiFactory = KtPsiFactory(element.project)

            val propertyName = element.name ?: return
            val propertyType = element.typeReference?.text ?: return
            val backingPropertyName = context.backingProperty.name ?: return
            val backingPropertyType = context.backingProperty.typeReference?.text

            val containingFile = element.containingKtFile
            val referencesToReplace = containingFile.collectDescendantsOfType<KtNameReferenceExpression>()
                .filter { ref ->
                    ref.getReferencedName() == backingPropertyName && ref.mainReference.resolve() == context.backingProperty
                }
                .map { updater.getWritable(it) }

            val backingProperty = updater.getWritable(context.backingProperty)

            referencesToReplace.forEach { writableRef ->
                writableRef.replace(psiFactory.createExpression(propertyName))
            }

            val newPropertyText = buildString {
                element.modifierList?.text?.let { modifiers ->
                    if (modifiers.isNotBlank()) {
                        append(modifiers)
                        append(" ")
                    }
                }
                append(if (element.isVar) "var" else "val")
                append(" ")
                append(propertyName)
                append(": ")
                append(propertyType)

                val initializer = context.backingProperty.initializer
                if (initializer != null) {
                    append("\nfield = ")
                    append(initializer.text)
                } else if (backingPropertyType != null && backingPropertyType != propertyType) {
                    append("\nfield: ")
                    append(backingPropertyType)
                }
            }

            val newProperty = psiFactory.createProperty(newPropertyText)
            element.replace(newProperty).reformat(canChangeWhiteSpacesOnly = true)

            backingProperty.parent.deleteChildRange(
                backingProperty,
                backingProperty.siblings(withSelf = false).takeWhile { it is PsiWhiteSpace }.lastOrNull() ?: backingProperty
            )
            backingProperty.delete()
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = propertyVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (element.isVar && element.setter != null) return false
        if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD) && !element.hasModifier(KtTokens.FINAL_KEYWORD)) return false
        return !element.isPrivate() && element.getter != null
    }

    override fun getApplicableRanges(element: KtProperty): List<TextRange> = ApplicabilityRange.single(element) { it.getter }

    override fun KaSession.prepareContext(element: KtProperty): Context? {
        val returnedProperty = getReturnedPropertyFromGetter(element.getter) ?: return null

        val allProperties = (element.parent as? KtElement)
            ?.childrenOfType<KtProperty>()
            ?.filter { it != element }
            ?: emptyList()

        if (!allProperties.contains(returnedProperty)) return null

        val returnedType = returnedProperty.symbol.returnType
        val propertyType = element.symbol.returnType

        if (returnedType.semanticallyEquals(propertyType)) return null
        if (!returnedType.isSubtypeOf(propertyType)) return null

        return Context(returnedProperty)
    }

    context(_: KaSession)
    private fun getReturnedPropertyFromGetter(getter: KtPropertyAccessor?): KtProperty? {
        val body = getter?.bodyExpression ?: return null
        val returnedExpr = when (body) {
            is KtNameReferenceExpression -> body
            is KtBlockExpression -> {
                val returnExpr = body.statements.singleOrNull() as? KtReturnExpression
                returnExpr?.returnedExpression as? KtNameReferenceExpression
            }

            else -> null
        }
        val returnedProperty = returnedExpr?.let { resolveToProperty(it) } ?: return null
        if (!returnedProperty.isPrivate()) return null
        if (returnedProperty.isVar) return null
        if (returnedProperty.hasDelegate()) return null
        if (returnedProperty.getter != null) return null
        return returnedProperty
    }

    context(_: KaSession)
    private fun resolveToProperty(expression: KtNameReferenceExpression): KtProperty? {
        val symbol = expression.mainReference.resolveToSymbol() as? KaPropertySymbol ?: return null
        return symbol.psi as? KtProperty
    }

}
