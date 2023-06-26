// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlinePropertyHandler
import org.jetbrains.kotlin.idea.util.nameIdentifierTextRangeInThis
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext.DECLARATION_TO_DESCRIPTOR
import org.jetbrains.kotlin.resolve.BindingContext.REFERENCE_TARGET
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class UnnecessaryVariableInspection : AbstractApplicabilityBasedInspection<KtProperty>(KtProperty::class.java) {
    override fun isApplicable(element: KtProperty): Boolean =
        statusFor(element) != null

    override fun inspectionHighlightRangeInElement(element: KtProperty): TextRange? =
        element.nameIdentifierTextRangeInThis()

    override fun inspectionHighlightType(element: KtProperty): ProblemHighlightType {
        val hasMultiLineBlock = element.initializer?.hasMultiLineBlock() == true
        return if (hasMultiLineBlock) ProblemHighlightType.INFORMATION else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    }

    override fun inspectionText(element: KtProperty): String = when (statusFor(element)) {
        Status.RETURN_ONLY -> KotlinBundle.message("variable.used.only.in.following.return.and.should.be.inlined")
        Status.EXACT_COPY -> KotlinBundle.message(
            "variable.is.same.as.0.and.should.be.inlined",
            (element.initializer as? KtNameReferenceExpression)?.getReferencedName().toString()
        )
        else -> ""
    }

    override val defaultFixText: String
        get() = KotlinBundle.message("inline.variable")

    override val startFixInWriteAction: Boolean = false

    override fun applyTo(element: KtProperty, project: Project, editor: Editor?) {
        KotlinInlinePropertyHandler(withPrompt = false).inlineElement(project, editor, element)
    }

    private fun LeafPsiElement.startsMultilineBlock(): Boolean =
        node.elementType == KtTokens.LBRACE && parent.safeAs<KtExpression>()?.isMultiLine() == true

    private fun KtExpression.hasMultiLineBlock(): Boolean =
        anyDescendantOfType<LeafPsiElement> { it.startsMultilineBlock() }

    companion object {
        private enum class Status {
            RETURN_ONLY,
            EXACT_COPY
        }

        private fun statusFor(property: KtProperty): Status? {
            if (!property.isLocal) return null
            if (property.annotationEntries.isNotEmpty()) return null
            val enclosingElement = KtPsiUtil.getEnclosingElementForLocalDeclaration(property) ?: return null
            val initializer = property.initializer ?: return null
            if (property.hasComment()) return null

            fun isExactCopy(): Boolean {
                if (property.isVar || initializer !is KtNameReferenceExpression || property.typeReference != null) return false

                val initializerDescriptor = initializer.resolveToCall(BodyResolveMode.FULL)?.resultingDescriptor as? VariableDescriptor
                    ?: return false
                if (initializerDescriptor.isVar) return false
                if (initializerDescriptor.containingDeclaration !is FunctionDescriptor) return false
                if (initializerDescriptor.safeAs<LocalVariableDescriptor>()?.isDelegated == true) return false

                val copyName = initializerDescriptor.name.asString()
                if (ReferencesSearch.search(property, LocalSearchScope(enclosingElement)).findFirst() == null) return false

                val containingDeclaration = property.getStrictParentOfType<KtDeclaration>() ?: return true

                val validator = Fe10KotlinNewDeclarationNameValidator(
                    container = containingDeclaration,
                    anchor = property,
                    target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                    excludedDeclarations = listOfNotNull(
                        DescriptorToSourceUtils.descriptorToDeclaration(initializerDescriptor) as? KtDeclaration
                    )
                )
                return validator(copyName)
            }

            fun isReturnOnly(): Boolean {
                val nextStatement = property.getNextSiblingIgnoringWhitespaceAndComments() as? KtReturnExpression ?: return false
                val returned = nextStatement.returnedExpression as? KtNameReferenceExpression ?: return false
                val context = nextStatement.analyze()
                return context[REFERENCE_TARGET, returned] == context[DECLARATION_TO_DESCRIPTOR, property]
            }

            return when {
                isExactCopy() -> Status.EXACT_COPY
                isReturnOnly() -> Status.RETURN_ONLY
                else -> null
            }
        }

        private fun KtProperty.hasComment(): Boolean {
            fun Sequence<PsiElement>.firstComment() =
                takeWhile { it is PsiWhiteSpace || it is PsiComment }.firstIsInstanceOrNull<PsiComment>()
            return prevLeafs.firstComment() != null ||
                    initializer?.nextLeafs?.firstComment()?.takeIf { it.getLineNumber() == this.getLineNumber() } != null
        }
    }
}
