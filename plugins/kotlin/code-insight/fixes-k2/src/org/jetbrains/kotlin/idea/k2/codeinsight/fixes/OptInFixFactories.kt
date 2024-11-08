// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KaNamedAnnotationValue
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.fir.utils.getActualAnnotationTargets
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix
import org.jetbrains.kotlin.idea.quickfix.OptInGeneralUtilsBase
import org.jetbrains.kotlin.idea.refactoring.isOpen
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.checkers.OptInNames

internal object OptInFixFactories {

    val optInUsageFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInUsage ->
        createQuickFix(diagnostic)
    }

    val optInUsageErrorFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInUsageError ->
        createQuickFix(diagnostic)
    }

    val optInToInheritanceFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInToInheritance ->
        createQuickFix(diagnostic)
    }

    val optInToInheritanceErrorFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInToInheritanceError ->
        createQuickFix(diagnostic)
    }

    val optInOverrideFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInOverride ->
        createQuickFix(diagnostic)
    }

    val optInOverrideErrorFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInOverrideError ->
        createQuickFix(diagnostic)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
    private fun createQuickFix(diagnostic: KaFirDiagnostic<PsiElement>): List<AddAnnotationFix> {
        val element = diagnostic.psi.findParentOfType<KtElement>(strict = false) ?: return emptyList()
        val annotationClassId = OptInFixUtils.optInMarkerClassId(diagnostic) ?: return emptyList()

        val optInClassId = ClassId.topLevel(OptInFixUtils.optInFqName() ?: return emptyList())
        val isOverrideError = isOverrideError(diagnostic)

        val annotationSymbol = OptInFixUtils.findAnnotation(annotationClassId) ?: return emptyList()
        if (!OptInFixUtils.annotationIsVisible(annotationSymbol, from = element)) return emptyList()

        val applicableTargets = annotationSymbol.annotationApplicableTargets
        val result = mutableListOf<AddAnnotationFix>()

        val candidates = OptInGeneralUtils.collectCandidates(element)

        fun collectPropagateOptInAnnotationFix(targetElement: KtElement, kind: AddAnnotationFix.Kind): AddAnnotationFix? {
            if (targetElement !is KtDeclaration) return null
            if (applicableTargets == null) return null

            val actualTargetList = targetElement.symbol.getActualAnnotationTargets() ?: return null
            return OptInGeneralUtils.collectPropagateOptInAnnotationFix(
                targetElement,
                kind,
                applicableTargets,
                actualTargetList,
                annotationClassId,
                isOverrideError
            )
        }

        candidates.forEach { (targetElement, kind) ->
            result.addIfNotNull(collectPropagateOptInAnnotationFix(targetElement, kind))
            result.add(OptInGeneralUtils.collectUseOptInAnnotationFix(targetElement, kind, optInClassId, annotationClassId.asSingleFqName(), isOverrideError))
        }
        return result
    }

    private fun isOverrideError(diagnostic: KaFirDiagnostic<PsiElement>): Boolean =
        diagnostic is KaFirDiagnostic.OptInOverride || diagnostic is KaFirDiagnostic.OptInOverrideError
}

private object OptInGeneralUtils : OptInGeneralUtilsBase() {

    override fun KtDeclaration.isSubclassOptPropagateApplicable(annotationFqName: FqName): Boolean {
        if (this !is KtClass) return false
        // SubclassOptInRequired is inapplicable on sealed classes and interfaces, final classes,
        // open local classes, object, enum classes and fun interfaces
        check(!this.isLocal) { "Local declarations are filtered in OptInFixesFactory.doCreateActions" }
        if (this.isSealed() || this.hasModifier(KtTokens.FUN_KEYWORD) || !this.isOpen()) return false
        if (KotlinPsiHeuristics.hasAnnotation(this, OptInNames.SUBCLASS_OPT_IN_REQUIRED_FQ_NAME)) return false

        analyze(this) {
            return superTypeListEntries.any {
                val typeReference = it.typeReference
                val resolvedClass = typeReference?.type?.expandedSymbol ?: return false
                val classAnnotation = resolvedClass.annotations[OptInNames.SUBCLASS_OPT_IN_REQUIRED_CLASS_ID].firstOrNull()
                classAnnotation != null && annotationFqName.isInSubclassArguments(classAnnotation)
            }
        }
    }

    private fun FqName.isInSubclassArguments(subclassOptInAnnotation: KaAnnotation): Boolean {
        val argument = subclassOptInAnnotation.arguments.find { arg -> arg.name == OptInNames.OPT_IN_ANNOTATION_CLASS } ?: return false
        val classIds = getSubclassArgClassIds(argument)
        return classIds.any {
            !it.isLocal && it.asSingleFqName() == this
        }
    }

    private fun getSubclassArgClassIds(argument: KaNamedAnnotationValue): List<ClassId> {
        return when (val expression = argument.expression) {
            // @SubclassOptInRequired for stdlib versions below 2.1
            is KaAnnotationValue.ClassLiteralValue -> listOfNotNull((expression.type as? KaClassType)?.classId)
            // @SubclassOptInRequired for stdlib versions 2.1 and above
            is KaAnnotationValue.ArrayValue -> expression.values.mapNotNull { (it as? KaAnnotationValue.ClassLiteralValue)?.classId }
            else -> emptyList()
        }
    }
}