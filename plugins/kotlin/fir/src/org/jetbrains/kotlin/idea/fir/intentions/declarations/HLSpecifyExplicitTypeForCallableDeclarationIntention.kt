// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.intentions.declarations

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.with
import org.jetbrains.kotlin.idea.fir.api.AbstractHLIntention
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicabilityRange
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.fir.applicators.CallableReturnTypeUpdaterApplicator
import org.jetbrains.kotlin.idea.fir.applicators.CallableReturnTypeUpdaterApplicator.TypeInfo.Companion.createByKtTypes
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.util.bfs

class HLSpecifyExplicitTypeForCallableDeclarationIntention :
    AbstractHLIntention<KtCallableDeclaration, CallableReturnTypeUpdaterApplicator.TypeInfo>(KtCallableDeclaration::class, applicator) {
    override val applicabilityRange: HLApplicabilityRange<KtCallableDeclaration> = ApplicabilityRanges.DECLARATION_WITHOUT_INITIALIZER

    override val inputProvider = inputProvider<KtCallableDeclaration, CallableReturnTypeUpdaterApplicator.TypeInfo> { declaration ->
        val diagnostics = declaration.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).map { it::class }.toSet()
        // Avoid redundant intentions
        if (KtFirDiagnostic.AmbiguousAnonymousTypeInferred::class in diagnostics ||
            KtFirDiagnostic.PropertyWithNoTypeNoInitializer::class in diagnostics ||
            KtFirDiagnostic.MustBeInitialized::class in diagnostics
        ) return@inputProvider null
        getTypeInfo(declaration)
    }

    companion object {
        fun KtAnalysisSession.getTypeInfo(declaration: KtCallableDeclaration): CallableReturnTypeUpdaterApplicator.TypeInfo {
            val declarationType = declaration.getReturnKtType()
            val overriddenTypes = (declaration.getSymbol() as? KtCallableSymbol)?.getDirectlyOverriddenSymbols()
                ?.map { it.annotatedType.type }
                ?.distinct()
                ?: emptyList()
            val cannotBeNull = overriddenTypes.any { !it.canBeNull }
            val allTypes = (listOf(declarationType) + overriddenTypes)
                // Here we do BFS manually rather than invoke `getAllSuperTypes` because we have multiple starting points. Simply calling
                // `getAllSuperTypes` does not work because it would BFS traverse each starting point and put the result together, in which
                // case, for example, calling `getAllSuperTypes` would put `Any` at middle if one of the super type in the hierarchy has
                // multiple super types.
                .bfs { it.getDirectSuperTypes(shouldApproximate = true).iterator() }
                .map { it.approximateToSuperPublicDenotableOrSelf() }
                .distinct()
                .let { types ->
                    when {
                        cannotBeNull -> types.map { it.withNullability(KtTypeNullability.NON_NULLABLE) }.distinct()
                        declarationType.hasFlexibleNullability -> types.flatMap { type ->
                            listOf(type.withNullability(KtTypeNullability.NON_NULLABLE), type.withNullability(KtTypeNullability.NULLABLE))
                        }
                        else -> types
                    }
                }.toList()

            return with(CallableReturnTypeUpdaterApplicator.TypeInfo) {
                if (ApplicationManager.getApplication().isUnitTestMode) {
                    selectForUnitTest(declaration, allTypes)?.let { return it }
                }

                val approximatedDefaultType = declarationType.approximateToSuperPublicDenotableOrSelf().let {
                    if (cannotBeNull) it.withNullability(KtTypeNullability.NON_NULLABLE)
                    else it
                }
                createByKtTypes(
                    approximatedDefaultType,
                    allTypes.drop(1), // The first type is always the default type so we drop it.
                    useTemplate = true
                )
            }
        }

        // The following logic is copied from FE1.0 at
        // org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention.Companion#createTypeExpressionForTemplate
        private fun KtAnalysisSession.selectForUnitTest(
            declaration: KtCallableDeclaration,
            allTypes: List<KtType>
        ): CallableReturnTypeUpdaterApplicator.TypeInfo? {
            // This helps to be sure no nullable types are suggested
            if (declaration.containingKtFile.findDescendantOfType<PsiComment>()?.takeIf {
                    it.text == "// CHOOSE_NULLABLE_TYPE_IF_EXISTS"
                } != null) {
                val targetType = allTypes.firstOrNull { it.isMarkedNullable } ?: allTypes.first()
                return createByKtTypes(targetType)
            }
            // This helps to be sure something except Nothing is suggested
            if (declaration.containingKtFile.findDescendantOfType<PsiComment>()?.takeIf {
                    it.text == "// DO_NOT_CHOOSE_NOTHING"
                } != null
            ) {
                // Note that `isNothing` returns true for both `Nothing` and `Nothing?`
                val targetType = allTypes.firstOrNull { !it.isNothing } ?: allTypes.first()
                return createByKtTypes(targetType)
            }
            return null
        }

        val applicator = CallableReturnTypeUpdaterApplicator.applicator.with {
            isApplicableByPsi { declaration: KtCallableDeclaration ->
                if (declaration is KtConstructor<*> || declaration is KtFunctionLiteral) return@isApplicableByPsi false
                declaration.typeReference == null && (declaration as? KtNamedFunction)?.hasBlockBody() != true
            }
            familyName(KotlinBundle.lazyMessage("specify.type.explicitly"))
            actionName { declaration, _ ->
                when (declaration) {
                    is KtFunction -> KotlinBundle.message("specify.return.type.explicitly")
                    else -> KotlinBundle.message("specify.type.explicitly")
                }
            }
        }
    }
}