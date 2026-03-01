// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.getApplicableUseSiteTargets
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddAnnotationUseSiteTargetModCommandAction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.parameterVisitor
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.checkers.OptInNames.OPT_IN_CLASS_ID

internal class AddExplicitTargetToParameterAnnotationInspection :
    KotlinApplicableInspectionBase<KtAnnotationEntry, List<AnnotationUseSiteTarget>>() {

    override fun isApplicableByPsi(element: KtAnnotationEntry): Boolean {
        val languageVersionSettings = element.languageVersionSettings
        // with this feature on the compiler reports a warning with a quick fix on the plugin side, the inspection is unnecessary
        if (languageVersionSettings.supportsFeature(LanguageFeature.AnnotationDefaultTargetMigrationWarning)) return false
        // with this feature on the default annotation targets are intuitive, adding an explicit target is optional
        if (languageVersionSettings.supportsFeature(LanguageFeature.PropertyParamAnnotationDefaultTargetMode)) return false
        if (element.useSiteTarget != null) return false
        return true
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtAnnotationEntry,
        context: List<AnnotationUseSiteTarget>,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val annotationTargets = context
        val action = object : AddAnnotationUseSiteTargetModCommandAction() {
            override fun getAnnotationTargets(
                context: ActionContext,
                element: KtAnnotationEntry,
            ): List<AnnotationUseSiteTarget> = annotationTargets
        }
        val fix = LocalQuickFix.from(action) ?: error("Broken contract: unexpected null quick fix for non-null action")

        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("inspection.add.annotation.target.problem.description"),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ fix,
        )
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = parameterVisitor { parameter ->
        parameter.annotationEntries.forEach { annotationEntry ->
            visitTargetElement(annotationEntry, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtAnnotationEntry): List<AnnotationUseSiteTarget>? {
        if (isInAllowlist(element)) return null
        val targets = element.getApplicableUseSiteTargets()
        if (targets.isEmpty() || AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER !in targets) return null
        return when {
            AnnotationUseSiteTarget.PROPERTY in targets
                    || AnnotationUseSiteTarget.FIELD in targets && annotatedPropertyHasField(element) -> targets
            else -> null
        }
    }

    private fun KaSession.isInAllowlist(element: KtAnnotationEntry): Boolean {
        val annotationClassId = element.resolveToCall()?.singleConstructorCallOrNull()?.partiallyAppliedSymbol?.symbol?.containingClassId
        return annotationClassId in STANDARD_ANNOTATION_IDS_WITHOUT_NECESSARY_MIGRATION
    }

    private fun KaSession.annotatedPropertyHasField(annotationEntry: KtAnnotationEntry): Boolean {
        val parameter = annotationEntry.getStrictParentOfType<KtParameter>() ?: return false
        val parameterSymbol = parameter.symbol as? KaValueParameterSymbol ?: return false
        val parameterBasedProperty = parameterSymbol.generatedPrimaryConstructorProperty ?: return false
        if (!parameterBasedProperty.hasBackingField) return false
        // properties in annotation classes don't have backing fields, it is not reflected in AA/FIR
        val containingConstructor = parameterSymbol.containingDeclaration as? KaConstructorSymbol ?: return false
        val containingClass = containingConstructor.containingDeclaration as? KaClassSymbol ?: return false
        return containingClass.classKind != KaClassKind.ANNOTATION_CLASS
    }
}

// the allowlist is copied from FirAnnotationChecker
private val JAVA_LANG_PACKAGE = FqName("java.lang")
private val STANDARD_ANNOTATION_IDS_WITHOUT_NECESSARY_MIGRATION: Set<ClassId> = hashSetOf(
    OPT_IN_CLASS_ID,
    StandardClassIds.Annotations.Deprecated,
    StandardClassIds.Annotations.DeprecatedSinceKotlin,
    StandardClassIds.Annotations.Suppress,
    ClassId(JAVA_LANG_PACKAGE, Name.identifier("Deprecated")),
    ClassId(JAVA_LANG_PACKAGE, Name.identifier("SuppressWarnings")),
)
