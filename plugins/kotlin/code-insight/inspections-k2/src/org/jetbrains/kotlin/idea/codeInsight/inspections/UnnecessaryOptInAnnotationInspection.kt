// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.directlyOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.components.varargArrayType
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.WasExperimentalOptInsNecessityChecker
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitor
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.annotationEntryVisitor
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.checkers.OptInNames

private val OLD_EXPERIMENTAL_CLASS_ID = ClassId.topLevel(FqNames.OptInFqNames.OLD_EXPERIMENTAL_FQ_NAME)
private val OLD_USE_EXPERIMENTAL_CLASS_ID = ClassId.topLevel(FqNames.OptInFqNames.OLD_USE_EXPERIMENTAL_FQ_NAME)
private val OPT_IN_SHORT_NAMES = FqNames.OptInFqNames.OPT_IN_FQ_NAMES.map { it.shortName().asString() }.toSet()

internal class UnnecessaryOptInAnnotationInspection :
    KotlinApplicableInspectionBase<KtAnnotationEntry, UnnecessaryOptInAnnotationInspection.Context>() {

    internal data class Context(
        val unusedMarkers: List<UnusedMarker>,
        val allRedundant: Boolean,
    )

    internal data class UnusedMarker(
        val expression: KtClassLiteralExpression,
        val classId: ClassId,
    )

    override fun isApplicableByPsi(element: KtAnnotationEntry): Boolean = element.valueArguments.isNotEmpty()

    override fun getApplicableRanges(element: KtAnnotationEntry): List<TextRange> = ApplicabilityRange.self(element)

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtAnnotationEntry): Context? {
        val annotationType = element.typeReference?.type ?: return null
        val isOptIn = annotationType.isClassType(OptInNames.OPT_IN_CLASS_ID)
                || annotationType.isClassType(OLD_USE_EXPERIMENTAL_CLASS_ID)
        if (!isOptIn) return null

        val annotationEntryArguments = element.valueArguments
        val resolvedMarkers = mutableListOf<UnusedMarker>()
        for (arg in annotationEntryArguments) {
            val argumentExpression = arg.getArgumentExpression() as? KtClassLiteralExpression ?: continue
            val annotationValue = argumentExpression.evaluateAsAnnotationValue()
            val classId = (annotationValue as? KaAnnotationValue.ClassLiteralValue)?.classId ?: continue
            resolvedMarkers.add(UnusedMarker(argumentExpression, classId))
        }

        val owner = element.getStrictParentOfType<KtAnnotated>() ?: return null
        val moduleApiVersion = owner.languageVersionSettings.apiVersion
        val markerCollector = MarkerCollector(moduleApiVersion)
        owner.accept(OptInMarkerVisitor(), markerCollector)

        val unusedMarkers = resolvedMarkers.filter { markerCollector.isUnused(it.classId) }
        if (unusedMarkers.isEmpty()) return null

        return Context(unusedMarkers, allRedundant = annotationEntryArguments.size == unusedMarkers.size)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtAnnotationEntry,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        element, rangeInElement,
        KotlinBundle.message("inspection.unnecessary.opt_in.redundant.annotation"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly,
        RemoveAnnotationArgumentOrEntireEntry(),
    )

    override fun registerProblem(
        ranges: List<TextRange>,
        holder: ProblemsHolder,
        element: KtAnnotationEntry,
        context: Context,
        isOnTheFly: Boolean,
    ) {
        if (context.allRedundant) {
            super.registerProblem(ranges, holder, element, context, isOnTheFly)
        } else {
            context.unusedMarkers.forEach { marker ->
                holder.registerProblem(
                    marker.expression,
                    KotlinBundle.message(
                        "inspection.unnecessary.opt_in.redundant.marker",
                        marker.classId.shortClassName.asString()
                    ),
                    RemoveAnnotationArgumentOrEntireEntry(),
                )
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> {
        val file = holder.file
        val optInAliases = if (file is KtFile) KotlinPsiHeuristics.getImportAliases(file, OPT_IN_SHORT_NAMES) else emptySet()

        return annotationEntryVisitor { annotationEntry ->
            val entryShortName = annotationEntry.shortName?.asString()
            if (entryShortName != null && entryShortName !in OPT_IN_SHORT_NAMES && entryShortName !in optInAliases)
                return@annotationEntryVisitor

            visitTargetElement(annotationEntry, holder, isOnTheFly)
        }
    }
}

private class MarkerCollector(private val moduleApiVersion: ApiVersion) {
    private val foundMarkers = mutableSetOf<ClassId>()

    fun isUnused(marker: ClassId): Boolean = marker !in foundMarkers

    context(_: KaSession)
    fun collectMarkers(declaration: KtClassOrObject) {
        for (superTypeEntry in declaration.superTypeListEntries) {
            val superClassSymbol = superTypeEntry.typeReference?.type?.expandedSymbol ?: continue
            val annotation =
                superClassSymbol.annotations[OptInNames.SUBCLASS_OPT_IN_REQUIRED_CLASS_ID].firstOrNull() ?: continue
            val argument = annotation.arguments.firstOrNull { it.name == OptInNames.OPT_IN_ANNOTATION_CLASS } ?: continue
            val classIds = when (val expr = argument.expression) {
                is KaAnnotationValue.ClassLiteralValue -> listOfNotNull((expr.type as? KaClassType)?.classId)
                is KaAnnotationValue.ArrayValue -> expr.values.mapNotNull {
                    ((it as? KaAnnotationValue.ClassLiteralValue)?.type as? KaClassType)?.classId
                }
                else -> emptyList()
            }
            foundMarkers.addAll(classIds)
        }
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun collectMarkers(declaration: KtDeclaration) {
        if (declaration !is KtFunction && declaration !is KtProperty && declaration !is KtParameter) return

        if (declaration is KtParameter && declaration.isVarArg) {
            // effective type of vararg parameter is corresponding array:
            // e.g. IntArray for `varargs values: Int`, `ULongArray` for `vararg values: ULong`
            // hence, corresponding array types are present in declaration implicitly.

            val parameterSymbol =
                declaration.symbol as? KaValueParameterSymbol
            val annotatedSymbol =
                parameterSymbol?.varargArrayType?.expandedSymbol as? KaAnnotatedSymbol
            annotatedSymbol?.collectMarkers()
        }

        if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
        val symbol = declaration.symbol as? KaCallableSymbol ?: return

        symbol.directlyOverriddenSymbols.forEach { overriddenSymbol ->
            (overriddenSymbol as? KaAnnotatedSymbol)?.collectMarkers()
        }
    }

    context(_: KaSession)
    fun collectMarkers(expression: KtReferenceExpression) {
        val symbols = expression.mainReference.resolveToSymbols()
        for (symbol in symbols) {
            (symbol as? KaAnnotatedSymbol)?.collectMarkers()

            if (symbol is KaConstructorSymbol) {
                val containingClassId = symbol.containingClassId
                if (containingClassId != null) {
                    (findClass(containingClassId) as? KaAnnotatedSymbol)?.collectMarkers()
                }
            }

            if (symbol is KaTypeAliasSymbol) {
                (symbol.expandedType.expandedSymbol as? KaAnnotatedSymbol)?.collectMarkers()
            }

            if (symbol is KaConstructorSymbol) {
                val callExpression = expression.parent as? KtCallExpression
                val callType = callExpression?.expressionType
                if (callType is KaClassType) {
                    // If `typealias B = A`, resolveToSymbols() for `B()` returns the constructor of A, not the type alias B.
                    // ensure abbreviation is used
                    callType.abbreviation?.symbol?.collectMarkers()
                }
            }

            if (symbol is KaPropertySymbol) {
                val setter = symbol.setter
                if (setter != null && expression.readWriteAccess(useResolveForReadWrite = false).isWrite) {
                    setter.collectMarkers()
                }
            }

            if (symbol is KaCallableSymbol) {
                if (symbol is KaFunctionSymbol) {
                    symbol.valueParameters.forEach { it.returnType.collectTypeMarkers() }
                }
                symbol.returnType.collectTypeMarkers()
                symbol.receiverType?.collectTypeMarkers()
            }

            generateSequence(symbol) { s ->
                val containingSymbol = s.containingSymbol as? KaNamedClassSymbol
                if (containingSymbol?.classKind?.isObject == true || containingSymbol?.containingSymbol is KaPackageSymbol) containingSymbol else null
            }.forEach { (it as? KaNamedClassSymbol)?.collectMarkers() }
        }
    }

    context(_: KaSession)
    fun collectMarkers(delegate: KtPropertyDelegate) {
        val expression = delegate.expression
        if (expression is KtReferenceExpression) {
            val symbols = expression.mainReference.resolveToSymbols()
            symbols.forEach {(it as? KaAnnotatedSymbol)?.collectMarkers() }
        }

        delegate.references.filterIsInstance<KtReference>().forEach {
            it.resolveToSymbols().forEach { symbol ->
                (symbol as? KaAnnotatedSymbol)?.collectMarkers()
            }
        }
    }

    context(_: KaSession)
    private fun KaType.collectTypeMarkers() {
        if (this is KaClassType) {
            typeArguments.forEach { arg ->
                arg.type?.collectTypeMarkers()
            }
            abbreviation?.symbol?.collectMarkers()
        }
        expandedSymbol?.collectMarkers()
    }

    context(_: KaSession)
    private fun KaAnnotatedSymbol.collectMarkers() {
        for (annotation in annotations) {
            val annotationClassId = annotation.classId ?: continue
            val annotationClassSymbol = findClass(annotationClassId) ?: continue

            if (annotationClassSymbol.annotations.contains(OptInNames.REQUIRES_OPT_IN_CLASS_ID) ||
                annotationClassSymbol.annotations.contains(OLD_EXPERIMENTAL_CLASS_ID)
            ) {
                foundMarkers += annotationClassId
            }
        }
        WasExperimentalOptInsNecessityChecker
            .getNecessaryOptInsFromWasExperimental(annotations, moduleApiVersion)
            .forEach { foundMarkers += it }

        if (this is KaClassSymbol) {
            (containingDeclaration as? KaAnnotatedSymbol)?.collectMarkers()
        }
    }
}

private class OptInMarkerVisitor : KtTreeVisitor<MarkerCollector>() {
    override fun visitNamedDeclaration(declaration: KtNamedDeclaration, markerCollector: MarkerCollector): Void? {
        analyze(declaration) {
            markerCollector.collectMarkers(declaration)
        }
        return super.visitNamedDeclaration(declaration, markerCollector)
    }

    override fun visitParameter(parameter: KtParameter, markerCollector: MarkerCollector): Void? {
        analyze(parameter) {
            markerCollector.collectMarkers(parameter)
        }
        return super.visitParameter(parameter, markerCollector)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression, markerCollector: MarkerCollector): Void? {
        analyze(expression) {
            markerCollector.collectMarkers(expression)
        }
        return super.visitReferenceExpression(expression, markerCollector)
    }

    override fun visitClassOrObject(expression: KtClassOrObject, markerCollector: MarkerCollector): Void? {
        analyze(expression) {
            markerCollector.collectMarkers(expression)
        }
        return super.visitClassOrObject(expression, markerCollector)
    }

    override fun visitPropertyDelegate(delegate: KtPropertyDelegate, markerCollector: MarkerCollector): Void? {
        analyze(delegate) {
            markerCollector.collectMarkers(delegate)
        }
        return super.visitPropertyDelegate(delegate, markerCollector)
    }
}

private class RemoveAnnotationArgumentOrEntireEntry : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.unnecessary.opt_in.remove.marker.fix.family.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val psiElement = descriptor.psiElement ?: return
        if (psiElement is KtAnnotationEntry) {
            psiElement.delete()
            return
        }
        val valueArgument = psiElement.parentOfType<KtValueArgument>() ?: return
        val annotationEntry = valueArgument.parentOfType<KtAnnotationEntry>() ?: return
        if (annotationEntry.valueArguments.size == 1) {
            annotationEntry.delete()
        } else {
            annotationEntry.valueArgumentList?.removeArgument(valueArgument)
        }
    }
}