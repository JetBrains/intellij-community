// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

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

                val indent = getIndent(element)
                val initializer = context.backingProperty.initializer
                if (initializer != null) {
                    append("\n$indent field = ")
                    append(initializer.text)
                } else if (backingPropertyType != null && backingPropertyType != propertyType) {
                    append("\n$indent field: ")
                    append(backingPropertyType)
                }
            }

            val newProperty = psiFactory.createProperty(newPropertyText)
            element.replace(newProperty)

            backingProperty.delete()
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ) = object : KtVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            visitTargetElement(property, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtProperty): Context? {
        if (element.isPrivate()) return null
        val getter = element.getter ?: return null

        val returnedProperty = getReturnedPropertyFromGetter(getter) ?: return null
        if (!returnedProperty.isPrivate()) return null

        val allProperties = (element.parent as? KtElement)
            ?.children
            ?.filterIsInstance<KtProperty>()
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
    private fun getReturnedPropertyFromGetter(getter: KtPropertyAccessor): KtProperty? {
        val returnedExpr = when (val body = getter.bodyExpression ?: return null) {
            is KtNameReferenceExpression -> body
            is KtBlockExpression -> {
                val returnExpr = body.statements.singleOrNull() as? KtReturnExpression
                returnExpr?.returnedExpression as? KtNameReferenceExpression
            }

            else -> null
        }
        return returnedExpr?.let { resolveToProperty(it) }
    }

    context(_: KaSession)
    private fun resolveToProperty(expression: KtNameReferenceExpression): KtProperty? {
        val symbol = expression.mainReference.resolveToSymbol() as? KaPropertySymbol ?: return null
        return symbol.psi as? KtProperty
    }

    private fun getIndent(element: KtProperty): String {
        val file = element.containingKtFile
        val indentOptions = CodeStyle.getIndentOptions(file)
        val parentIndent = CodeStyleManager.getInstance(file.project).getLineIndent(file, element.parent.startOffset) ?: ""
        return if (indentOptions.USE_TAB_CHARACTER) "$parentIndent\t" else "$parentIndent${" ".repeat(indentOptions.INDENT_SIZE)}"
    }

    private fun KtProperty.isPrivate(): Boolean = modifierList?.hasModifier(KtTokens.PRIVATE_KEYWORD) ?: false
}
