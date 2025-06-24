// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.MutableCollectionsConversionUtils
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.types.Variance

class SuspiciousCollectionReassignmentInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        binaryExpressionVisitor(fun(binaryExpression) {
            analyze(binaryExpression) {
                if (binaryExpression.right == null) return
                val operationToken = binaryExpression.operationToken as? KtSingleValueToken ?: return
                if (operationToken !in targetOperations) return
                val left = binaryExpression.left ?: return
                val property = left.mainReference?.resolve() as? KtProperty ?: return
                if (!property.isVar) return

                val leftType = left.expressionType as? KaClassType ?: return
                if (!leftType.isReadOnlyCollectionOrMap()) return

                // TODO there are no tests for this check; add the tests or remove it
                @OptIn(KaExperimentalApi::class)
                val diagnostics = binaryExpression.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                if (diagnostics.any { it.severity == KaSeverity.ERROR }) return

                val fixes = mutableListOf<LocalQuickFix>()
                if (MutableCollectionsConversionUtils.canConvertPropertyType(property)) {
                    fixes += ChangeTypeToMutableFix(leftType.classId)
                }
                if (ReplaceWithFilterFix.run { isApplicable(binaryExpression, leftType) }) {
                    fixes.add(ReplaceWithFilterFix())
                }
                when {
                    ReplaceWithAssignmentFix.run { isApplicable(binaryExpression, property) } -> fixes.add(ReplaceWithAssignmentFix())
                    JoinWithInitializerFix.isApplicable(binaryExpression, property) -> fixes.add(JoinWithInitializerFix(operationToken))
                }
                if (fixes.isEmpty()) return

                @OptIn(KaExperimentalApi::class)
                val typeText = leftType.render(renderer = KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT).takeWhile { it != '<' }.lowercase()
                val operationReference = binaryExpression.operationReference
                holder.registerProblem(
                    operationReference,
                    KotlinBundle.message("0.on.a.readonly.1.creates.a.new.1.under.the.hood", operationReference.text, typeText),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    *fixes.toTypedArray()
                )
            }
        })

    private class ChangeTypeToMutableFix(private val immutableCollectionClassId: ClassId) :
        KotlinModCommandQuickFix<KtOperationReferenceExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("change.type.to.mutable.fix.text")

        override fun applyFix(
            project: Project,
            element: KtOperationReferenceExpression,
            updater: ModPsiUpdater,
        ) {
            val binaryExpression = element.parent as? KtBinaryExpression ?: return
            val left = binaryExpression.left ?: return
            val property = left.mainReference?.resolve() as? KtProperty ?: return

            val psiFactory = KtPsiFactory(project)
            MutableCollectionsConversionUtils.convertDeclarationTypeToMutable(property, immutableCollectionClassId, psiFactory)
            property.valOrVarKeyword.replace(psiFactory.createValKeyword())
            updater.moveCaretTo(property.endOffset)
        }
    }

    private class ReplaceWithFilterFix : KotlinModCommandQuickFix<KtOperationReferenceExpression>() {
        override fun getName() = KotlinBundle.message("replace.with.filter.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, element: KtOperationReferenceExpression, updater: ModPsiUpdater) {
            val operationReference = element
            val binaryExpression = operationReference.parent as? KtBinaryExpression ?: return
            val left = binaryExpression.left ?: return
            val right = binaryExpression.right ?: return
            val psiFactory = KtPsiFactory(project)
            operationReference.replace(psiFactory.createOperationName(KtTokens.EQ.value))
            right.replace(psiFactory.createExpressionByPattern("$0.filter { it !in $1 }", left, right))
        }

        companion object {
            fun KaSession.isApplicable(binaryExpression: KtBinaryExpression, leftType: KaClassType): Boolean {
                if (binaryExpression.operationToken != KtTokens.MINUSEQ) return false
                if (leftType.classId == StandardClassIds.Map) return false

                val binaryExpressionRHSClassSymbol = binaryExpression.right?.expressionType?.symbol as? KaNamedClassSymbol ?: return false
                val iterableSymbol = findClass(StandardClassIds.Iterable) ?: return false

                return binaryExpressionRHSClassSymbol.isSubClassOf(iterableSymbol)
            }
        }
    }

    private class ReplaceWithAssignmentFix : KotlinModCommandQuickFix<KtOperationReferenceExpression>() {
        override fun getName() = KotlinBundle.message("replace.with.assignment.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, element: KtOperationReferenceExpression, updater: ModPsiUpdater) {
            val operationReference = element
            val psiFactory = KtPsiFactory(project)
            operationReference.replace(psiFactory.createOperationName(KtTokens.EQ.value))
        }

        companion object {
            val emptyCollectionFactoryMethods =
                listOf("emptyList", "emptySet", "emptyMap", "listOf", "setOf", "mapOf").map { "kotlin.collections.$it" }

            fun KaSession.isApplicable(binaryExpression: KtBinaryExpression, property: KtProperty): Boolean {
                if (binaryExpression.operationToken != KtTokens.PLUSEQ) return false

                if (!property.isLocal) return false
                val initializer = property.initializer as? KtCallExpression ?: return false

                if (initializer.valueArguments.isNotEmpty()) return false
                val initializerResultingSymbol = initializer.resolveToCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol
                val fqName = initializerResultingSymbol?.callableId?.asSingleFqName()?.asString()
                if (fqName !in emptyCollectionFactoryMethods) return false

                val binaryExpressionRHSType = binaryExpression.right?.expressionType ?: return false
                val initializerType = initializer.expressionType ?: return false

                if (!binaryExpressionRHSType.isSubtypeOf(initializerType)) return false

                if (binaryExpression.siblings(forward = false, withItself = false)
                        .filter { it != property }
                        .any { sibling -> sibling.anyDescendantOfType<KtSimpleNameExpression> { it.mainReference.resolve() == property } }
                ) return false

                return true
            }
        }
    }

    private class JoinWithInitializerFix(private val op: KtSingleValueToken) : KotlinModCommandQuickFix<KtOperationReferenceExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("join.with.initializer.fix.text")

        override fun applyFix(project: Project, element: KtOperationReferenceExpression, updater: ModPsiUpdater) {
            val binaryExpression = element.parent as? KtBinaryExpression ?: return
            val left = binaryExpression.left ?: return
            val right = binaryExpression.right ?: return
            val property = left.mainReference?.resolve() as? KtProperty ?: return
            val initializer = property.initializer ?: return

            val psiFactory = KtPsiFactory(project)
            val newOp = if (op == KtTokens.PLUSEQ) KtTokens.PLUS else KtTokens.MINUS
            val replaced = initializer.replaced(psiFactory.createExpressionByPattern("$0 $1 $2", initializer, newOp.value, right))
            binaryExpression.delete()
            updater.moveCaretTo(replaced.endOffset)
        }

        companion object {
            fun isApplicable(binaryExpression: KtBinaryExpression, property: KtProperty): Boolean {
                if (!property.isLocal || property.initializer == null) return false
                return binaryExpression.getPrevSiblingIgnoringWhitespaceAndComments() == property
            }
        }
    }
}

private val targetOperations: List<KtSingleValueToken> = listOf(KtTokens.PLUSEQ, KtTokens.MINUSEQ)

private fun KaClassType.isReadOnlyCollectionOrMap(): Boolean {
    return classId in listOf(StandardClassIds.List, StandardClassIds.Set, StandardClassIds.Map)
}