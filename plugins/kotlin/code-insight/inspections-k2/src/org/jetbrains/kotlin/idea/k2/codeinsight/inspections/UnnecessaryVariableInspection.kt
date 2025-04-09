// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.k2.refactoring.inline.KotlinInlinePropertyHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class UnnecessaryVariableInspection : AbstractKotlinInspection() {
    private enum class Status {
        RETURN_ONLY,
        EXACT_COPY
    }

    @JvmField
    var reportImmediatelyReturnedVariables = false

    override fun getOptionsPane(): OptPane = pane(
        checkbox(
            "reportImmediatelyReturnedVariables",
            KotlinBundle.message("inspection.unnecessary.variable.option.report.immediately.returned.variables")
        )
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = propertyVisitor { property ->
        if (!property.isLocal || property.annotationEntries.isNotEmpty() || property.hasComment() || property.hasDelegate()) {
            return@propertyVisitor
        }

        val status = statusFor(property)
        if (status != null) {
            val description = when (status) {
                Status.RETURN_ONLY -> KotlinBundle.message("variable.used.only.in.following.return.and.should.be.inlined")
                Status.EXACT_COPY -> KotlinBundle.message(
                    "variable.is.same.as.0.and.should.be.inlined",
                    (property.initializer as? KtNameReferenceExpression)?.getReferencedName().toString()
                )
            }

            val hasMultiLineBlock = property.initializer?.hasMultiLineBlock() == true
            val highlightType = if (hasMultiLineBlock) ProblemHighlightType.INFORMATION else ProblemHighlightType.GENERIC_ERROR_OR_WARNING

            val nameIdentifier = property.nameIdentifier
            val range = if (nameIdentifier != null) TextRange(0, nameIdentifier.textLength) else null

            holder.registerProblemWithoutOfflineInformation(
                property.nameIdentifier ?: property,
                description,
                isOnTheFly,
                highlightType,
                range,
                InlineVariableFix()
            )
        }
    }

    private fun statusFor(property: KtProperty): Status? {
        val enclosingElement = KtPsiUtil.getEnclosingElementForLocalDeclaration(property) ?: return null
        val initializer = property.initializer ?: return null

        fun KaSession.isExactCopy(): Boolean {
            if (property.isVar || initializer !is KtNameReferenceExpression || property.typeReference != null) return false

            val symbol = initializer.mainReference.resolveToSymbol()
            val initializerSymbol = symbol as? KaLocalVariableSymbol ?: symbol as? KaParameterSymbol ?: return false

            val isVal = initializerSymbol.isVal
            val isContainingSymbolFunction = initializerSymbol.containingSymbol is KaFunctionSymbol
            val hasDelegate = (initializerSymbol.psi as? KtProperty)?.hasDelegate() == true

            if (!isVal || !isContainingSymbolFunction || hasDelegate) return false

            val copyName = initializerSymbol.name.asString()
            if (ReferencesSearch.search(property, LocalSearchScope(enclosingElement)).findFirst() == null) return false

            val excludedDeclaration = initializerSymbol.psi as? KtDeclaration

            val nameValidator = KotlinDeclarationNameValidator(
                visibleDeclarationsContext = enclosingElement,
                checkVisibleDeclarationsContext = true,
                target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                excludedDeclarations = listOfNotNull(excludedDeclaration)
            )
            return nameValidator.validate(copyName)
        }

        fun KaSession.isReturnOnly(): Boolean {
            val nextStatement = property.getNextSiblingIgnoringWhitespaceAndComments() as? KtReturnExpression ?: return false
            val returned = nextStatement.returnedExpression as? KtNameReferenceExpression ?: return false

            val returnedSymbol = returned.mainReference.resolveToSymbol() as? KaNamedSymbol ?: return false

            val elementSymbol = property.symbol as? KaNamedSymbol ?: return false

            return returnedSymbol == elementSymbol
        }

        return when {
            analyze(property) { isExactCopy() } -> Status.EXACT_COPY
            reportImmediatelyReturnedVariables && analyze(property) { isReturnOnly() } -> Status.RETURN_ONLY
            else -> null
        }
    }

    private class InlineVariableFix : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("inline.variable")

        override fun startInWriteAction(): Boolean = false

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement.parent as? KtProperty ?: return
            KotlinInlinePropertyHandler().inlineElement(project, null, element)
        }
    }

    private fun LeafPsiElement.startsMultilineBlock(): Boolean =
        node.elementType == KtTokens.LBRACE && parent.safeAs<KtExpression>()?.isMultiLine() == true

    private fun KtExpression.hasMultiLineBlock(): Boolean =
        anyDescendantOfType<LeafPsiElement> { it.startsMultilineBlock() }

    private fun KtProperty.hasComment(): Boolean {
        fun Sequence<PsiElement>.firstComment() =
          takeWhile { it is PsiWhiteSpace || it is PsiComment }.firstIsInstanceOrNull<PsiComment>()
        return prevLeafs.firstComment() != null ||
               initializer?.nextLeafs?.firstComment()?.takeIf { it.getLineNumber() == this.getLineNumber() } != null
    }
}
