// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.names.FqNames.OptInFqNames
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.UnnecessaryOptInAnnotationInspection.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.classLiteralId
import org.jetbrains.kotlin.idea.codeinsights.impl.base.getRequiredOptIns
import org.jetbrains.kotlin.idea.codeinsights.impl.base.getRequiredOptInsToSubclass
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.renderer.render

internal class UnnecessaryOptInAnnotationInspection : KotlinApplicableInspectionBase<KtAnnotationEntry, Context>() {
    internal sealed interface Context {
        fun createProblemDescriptors(
            manager: InspectionManager,
            element: KtAnnotationEntry,
            rangeInElement: TextRange?,
            isOnTheFly: Boolean,
        ): List<ProblemDescriptor>
    }

    internal data class RemoveMarkersContext(
        val markersToRemove: Set<OptInMarker>,
    ) : Context {
        override fun createProblemDescriptors(
            manager: InspectionManager,
            element: KtAnnotationEntry,
            rangeInElement: TextRange?,
            isOnTheFly: Boolean
        ): List<ProblemDescriptor> =
            markersToRemove
                .mapNotNull {
                    val marker = it.markerPointer.element ?: return@mapNotNull null
                    manager.createProblemDescriptor(
                        /* psiElement = */ marker,
                        /* descriptionTemplate = */ KotlinBundle.message(
                            "inspection.unnecessary.opt_in.redundant.marker",
                            it.classId.shortClassName.render()
                        ),
                        /* fix = */ RemoveOptInMarkerQuickFix(),
                        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        /* onTheFly = */ isOnTheFly
                    )
                }
    }

    internal object RemoveEntireOptInAnnotationContext : Context {
        override fun createProblemDescriptors(
            manager: InspectionManager,
            element: KtAnnotationEntry,
            rangeInElement: TextRange?,
            isOnTheFly: Boolean
        ): List<ProblemDescriptor> = listOf(
            manager.createProblemDescriptor(
                /* psiElement = */ element,
                /* descriptionTemplate = */ KotlinBundle.message("inspection.unnecessary.opt_in.redundant.annotation"),
                /* fix = */ RemoveAnnotationEntryQuickFix(),
                /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                /* onTheFly = */ isOnTheFly
            )
        )
    }

    private val OPT_IN_CLASS_IDS = OptInFqNames.OPT_IN_FQ_NAMES.map(ClassId::topLevel).toSet()
    private val OPT_IN_SHORT_NAMES = OPT_IN_CLASS_IDS.map { it.shortClassName.asString() }.toSet()

    override fun isApplicableByPsi(element: KtAnnotationEntry): Boolean =
        element.valueArguments.isNotEmpty()

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitorVoid {
        // Filter out annotation usages that definitely _aren't_ a usage of @OptIn before we visit them.
        // We'll confirm that it _is_ an @OptIn usage via analysis later, but doing this check here can
        // save us from the need to analyze at all.
        // We do this check at the visitor level rather than in `isApplicableByPsi` so we only need to
        // do the KotlinPsiHeuristics search for import aliases once.
        val optInAliases =
            (holder.file as? KtFile)
                ?.let { KotlinPsiHeuristics.getImportAliases(it, OPT_IN_SHORT_NAMES) }
                .orEmpty()
        val optInNames = OPT_IN_SHORT_NAMES + optInAliases

        return annotationEntryVisitor { annotationEntry ->
            val entryShortName = annotationEntry.shortName?.asString()
            if (entryShortName != null && entryShortName !in optInNames)
                return@annotationEntryVisitor

            visitTargetElement(annotationEntry, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(entry: KtAnnotationEntry): Context? {
        val call = entry.resolveToCall()?.successfulConstructorCallOrNull() ?: return null
        if (call.symbol.containingClassId !in OPT_IN_CLASS_IDS) return null

        val removalCandidates = entry.valueArguments.mapNotNull { arg ->
            val argumentExpression = arg.getArgumentExpression() as? KtClassLiteralExpression
                ?: return@mapNotNull null

            val classId = argumentExpression.classLiteralId ?: return@mapNotNull null

            classId to argumentExpression.createSmartPointer()
        }.toMap()

        val visitor = OptInUsageVisitor(
            analysisSession = this@prepareContext,
            moduleApiVersion = entry.languageVersionSettings.apiVersion,
            markersToCheck = removalCandidates.keys,
            optInAnnotationEntry = entry,
        )

        entry.getStrictParentOfType<KtAnnotated>()?.accept(visitor)

        return when (visitor.unusedMarkers.size) {
            // Everything's in use, nothing to report.
            0 -> null
            // Nothing's in use, remove the entire @OptIn annotation.
            removalCandidates.size -> RemoveEntireOptInAnnotationContext
            // Some markers are unused, but not all - offer to remove those markers from @OptIn.
            else -> RemoveMarkersContext(
                visitor.unusedMarkers
                    .mapTo(mutableSetOf()) {
                        OptInMarker(removalCandidates[it]!!, it)
                    }
            )
        }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtAnnotationEntry,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): Nothing = error("Problem descriptors must be created directly by registerProblem")

    override fun registerProblem(
        ranges: List<TextRange>,
        holder: ProblemsHolder,
        element: KtAnnotationEntry,
        context: Context,
        isOnTheFly: Boolean
    ) {
        ranges.asSequence()
            .flatMap { range ->
                context.createProblemDescriptors(holder.manager, element, range, isOnTheFly)
            }
            .forEach(holder::registerProblem)
    }
}

internal data class OptInMarker(
    val markerPointer: SmartPsiElementPointer<KtClassLiteralExpression>,
    val classId: ClassId,
)

/**
 * A visitor that checks a tree for usages of the provided opt-in markers.
 *
 * When visiting is complete, [unusedMarkers] will contain the subset of the provided
 * markers that were never used.
 */
private class OptInUsageVisitor(
    private val analysisSession: KaSession,
    private val moduleApiVersion: ApiVersion,
    private val markersToCheck: Set<ClassId>,
    private val optInAnnotationEntry: KtAnnotationEntry,
) : KtTreeVisitorVoid() {
    private val markersNotSeen = markersToCheck.toMutableSet()

    private val visitedSymbols: MutableSet<KaAnnotatedSymbol> = mutableSetOf()
    private val visitedSupertypeSymbols: MutableSet<KaClassLikeSymbol> = mutableSetOf()
    private val visitedTypes: MutableSet<KaType> = mutableSetOf()

    val unusedMarkers: Set<ClassId>
        get() = markersNotSeen

    override fun visitElement(element: PsiElement) {
        // Optimization: stop walking the tree if we've confirmed that every opt-in marker we're
        // looking for is in use.
        if (unusedMarkers.isEmpty()) return
        super.visitElement(element)
    }

    context(KaSession)
    private fun KaAnnotatedSymbol.visit(followOverrides: Boolean = false) {
        if (!visitedSymbols.add(this)) return
        val usedOptIns = getRequiredOptIns(markersNotSeen, moduleApiVersion)
        markersNotSeen.removeAll(usedOptIns)

        when (this) {
            is KaClassSymbol, is KaConstructorSymbol -> {
                val outerClass = containingDeclaration as? KaClassLikeSymbol
                outerClass?.visit()
            }
            is KaCallableSymbol -> {
                if (followOverrides) {
                    if (this is KaNamedFunctionSymbol && isOverride ||
                        this is KaPropertySymbol && isOverride) {
                        directlyOverriddenSymbols.forEach { it.visit() }
                    }
                }
                if (this is KaValueParameterSymbol) {
                    generatedPrimaryConstructorProperty?.visit(followOverrides = followOverrides)
                }
            }
        }
    }

    context(KaSession)
    private fun KaClassLikeSymbol.visitForSubclassing() {
        if (!visitedSupertypeSymbols.add(this)) return
        val usedOptIns = getRequiredOptInsToSubclass(markersNotSeen)
        markersNotSeen.removeAll(usedOptIns)
    }

    context(KaSession)
    private fun KaType.visit() {
        if (!visitedTypes.add(this)) return
        abbreviation?.visit()
        when (this) {
            is KaClassType -> {
                symbol.visit()
                typeArguments.forEach {
                    it.type?.visit()
                }
            }
            is KaDefinitelyNotNullType -> original.visit()
            is KaFlexibleType -> {
                lowerBound.visit()
                upperBound.visit()
            }
            is KaIntersectionType -> conjuncts.forEach { it.visit() }
        }
    }

    context(KaSession)
    private fun KaCallableSignature<*>.visit() {
        returnType.visit()
        receiverType?.visit()

        if (this is KaFunctionSignature) {
            valueParameters.forEach { it.visit() }
        }
    }

    context(KaSession)
    private fun KaPartiallyAppliedSymbol<*, *>.visit() {
        dispatchReceiver?.type?.visit()
        extensionReceiver?.type?.visit()
        symbol.visit()
        signature.visit()
    }

    context(KaSession)
    private fun KaCall.visit() {
        when (this) {
            is KaSimpleVariableAccessCall -> {
                partiallyAppliedSymbol.visit()
                val symbol = this.symbol
                if (symbol is KaPropertySymbol) {
                    val accessor = when (simpleAccess) {
                        is KaSimpleVariableAccess.Read -> symbol.getter
                        is KaSimpleVariableAccess.Write -> symbol.setter
                    }
                    accessor?.visit()
                }
            }
            is KaCallableMemberCall<*, *> -> partiallyAppliedSymbol.visit()
            is KaCompoundVariableAccessCall -> {
                variablePartiallyAppliedSymbol.visit()
                val symbol = variablePartiallyAppliedSymbol.symbol
                if (symbol is KaPropertySymbol) {
                    symbol.getter?.visit()
                    symbol.setter?.visit()
                }
                compoundOperation.operationPartiallyAppliedSymbol.visit()
            }
            is KaCompoundArrayAccessCall -> {
                getPartiallyAppliedSymbol.visit()
                setPartiallyAppliedSymbol.visit()
                compoundOperation.operationPartiallyAppliedSymbol.visit()
            }
        }
    }

    context(KaSession)
    private fun KtReference.visit() {
        resolveToSymbols()
            .filterIsInstance<KaAnnotatedSymbol>()
            .forEach { it.visit() }
    }

    override fun visitKtElement(element: KtElement) {
        // Collect markers from callables called in scope.
        with(analysisSession) {
            super.visitKtElement(element)

            element.resolveToCall()?.successfulCallOrNull<KaCall>()?.visit()
        }
    }

    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
        // Collect markers from declarations and their overrides.
        with(analysisSession) {
            super.visitNamedDeclaration(declaration)
            if (declaration is KtParameter && declaration.isFunctionTypeParameter) return

            declaration.symbol.visit(followOverrides = true)
        }
    }

    /**
     * Collect opt-in markers from supertype entries of a class.
     *
     * These entries are only checked for @SubclassOptInRequired here - they'll be checked
     * for direct usage of opt-in markers in visitTypeReference below.
     */
    override fun visitSuperTypeEntry(specifier: KtSuperTypeEntry) {
        with(analysisSession) {
            super.visitSuperTypeEntry(specifier)

            val superClassType = specifier.typeReference?.type as? KaClassType ?: return
            superClassType.symbol.visitForSubclassing()
        }
    }

    override fun visitTypeReference(typeReference: KtTypeReference) {
        // Collect opt-in markers from referenced types.
        with(analysisSession) {
            super.visitTypeReference(typeReference)

            typeReference.type.visit()
        }
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        with(analysisSession) {
            super.visitTypeAlias(typeAlias)

            // Workaround for K1 AA bug - KtTypeReference in RHS of type alias doesn't directly resolve.
            // Instead, we find the KtType by the type alias's symbol.
            typeAlias.symbol.expandedType.visit()
        }
    }

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        // Stop walking if we hit the @OptIn entry itself, since we don't want to mark
        // its arguments as used.
        if (annotationEntry != optInAnnotationEntry) {
            super.visitAnnotationEntry(annotationEntry)
        }
    }

    override fun visitClassLiteralExpression(expression: KtClassLiteralExpression) {
        with(analysisSession) {
            super.visitClassLiteralExpression(expression)

            expression.receiverType?.visit()
        }
    }

    override fun visitPropertyDelegate(delegate: KtPropertyDelegate) {
        with(analysisSession) {
            super.visitPropertyDelegate(delegate)

            // Resolve the PSI references for the delegate expression. This will bring in
            // provideDelegate() for the RHS of the `by` expression, as well as `getValue()`
            // for the provided delegate.
            for (reference in delegate.references) {
                if (reference !is KtReference) continue
                reference.visit()
            }
        }
    }
}

/**
 * A quick fix that removes the targeted class-literal value argument from its containing annotation.
 *
 * If that argument was the last argument of the annotation, removes the entire annotation.
 */
private class RemoveOptInMarkerQuickFix : KotlinModCommandQuickFix<KtClassLiteralExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("inspection.unnecessary.opt_in.remove.marker.fix.family.name")

    override fun applyFix(
        project: Project,
        element: KtClassLiteralExpression,
        updater: ModPsiUpdater
    ) {
        val valueArgument = element.parentOfType<KtValueArgument>() ?: return
        val annotationEntry = valueArgument.parentOfType<KtAnnotationEntry>() ?: return
        if (annotationEntry.valueArguments.size == 1) {
            annotationEntry.delete()
        } else {
            annotationEntry.valueArgumentList?.removeArgument(valueArgument)
        }
    }
}

/** A quick fix that removes an entire unused @OptIn annotation. */
private class RemoveAnnotationEntryQuickFix : KotlinModCommandQuickFix<KtAnnotationEntry>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("inspection.unnecessary.opt_in.remove.annotation.fix.family.name")

    override fun applyFix(project: Project, element: KtAnnotationEntry, updater: ModPsiUpdater) {
        element.delete()
    }
}