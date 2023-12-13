// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtKClassAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.fir.utils.getActualAnnotationTargets
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactories
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
    val optInFixFactories = diagnosticFixFactories(
        KtFirDiagnostic.OptInUsage::class,
        KtFirDiagnostic.OptInUsageError::class,
        KtFirDiagnostic.OptInOverride::class,
        KtFirDiagnostic.OptInOverrideError::class
    ) { diagnostic -> createQuickFix(diagnostic) }

    context(KtAnalysisSession)
    private fun createQuickFix(diagnostic: KtFirDiagnostic<PsiElement>): List<AddAnnotationFix> {
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

            val actualTargetList = (targetElement as? KtDeclaration)?.getSymbol()?.getActualAnnotationTargets() ?: return null
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

    private fun isOverrideError(diagnostic: KtFirDiagnostic<PsiElement>): Boolean =
        diagnostic is KtFirDiagnostic.OptInOverride || diagnostic is KtFirDiagnostic.OptInOverrideError
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
                val resolvedClass = typeReference?.getKtType()?.expandedClassSymbol ?: return false
                val classAnnotation = resolvedClass.annotationsByClassId(OptInNames.SUBCLASS_OPT_IN_REQUIRED_CLASS_ID).firstOrNull()
                val annotationMarkerClass = classAnnotation?.arguments?.find { arg -> arg.name == OptInNames.OPT_IN_ANNOTATION_CLASS }
                val apiClassId = (annotationMarkerClass?.expression as? KtKClassAnnotationValue.KtNonLocalKClassAnnotationValue)?.classId
                apiClassId?.asSingleFqName() == annotationFqName
            }
        }
    }
}