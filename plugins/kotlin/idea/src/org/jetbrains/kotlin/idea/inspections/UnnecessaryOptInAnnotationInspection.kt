// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.getDirectlyOverriddenDeclarations
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.references.ReadWriteAccessChecker
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.SINCE_KOTLIN_FQ_NAME
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.descriptorUtil.*
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.aliasImportMap
import org.jetbrains.kotlin.utils.addToStdlib.constant
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * An inspection to detect unnecessary (obsolete) `@OptIn` annotations.
 *
 * The `@OptIn(SomeExperimentalMarker::class)` annotation is necessary for the code that
 * uses experimental library API marked with `@SomeExperimentalMarker` but is not experimental by itself.
 * When the library authors decide that the API is not experimental anymore, and they remove
 * the experimental marker, the corresponding `@OptIn` annotation in the client code becomes unnecessary
 * and may be removed so the people working with the code would not be misguided.
 *
 * For each `@OptIn` annotation, the inspection checks if in its scope there are names marked with
 * the experimental marker mentioned in the `@OptIn`, and it reports the marker classes that don't match
 * any names. For these redundant markers, the inspection proposes a quick fix to remove the marker
 * or the entire unnecessary `@OptIn` annotation if it contains a single marker.
 */
class UnnecessaryOptInAnnotationInspection : AbstractKotlinInspection() {

    /**
     * Get the PSI element to which the given `@OptIn` annotation applies.
     *
     * @receiver the `@OptIn` annotation entry
     * @return the annotated element, or null if no such element is found
     */
    private fun KtAnnotationEntry.getOwner(): KtElement? = getStrictParentOfType<KtAnnotated>()

    /**
     * A temporary storage for expected experimental markers.
     *
     * @param expression a smart pointer to the argument expression to create a quick fix
     * @param fqName the resolved fully qualified name
     */
    private data class ResolvedMarker(
        val expression: SmartPsiElementPointer<KtClassLiteralExpression>,
        val fqName: FqName
    )

    // Short names for `kotlin.OptIn` and `kotlin.UseExperimental` for faster comparison without name resolution
    private val USE_EXPERIMENTAL_SHORT_NAMES = OptInNames.USE_EXPERIMENTAL_FQ_NAMES.map { it.shortName().asString() }.toSet()

    /**
     * Main inspection visitor to traverse all annotation entries.
     */
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val optInAliases = holder.file.safeAs<KtFile>()
            ?.aliasImportMap()
            ?.entries()
            ?.filter { it.value in USE_EXPERIMENTAL_SHORT_NAMES }
            ?.mapNotNull { it.key }
            ?.toSet()
            ?: emptySet()

        return annotationEntryVisitor { annotationEntry  ->
            // Fast check if the annotation may be `@OptIn`/`@UseExperimental` or any of their import aliases
            val entryShortName = annotationEntry.shortName?.asString()
            if (entryShortName != null && entryShortName !in USE_EXPERIMENTAL_SHORT_NAMES && entryShortName !in optInAliases)
                return@annotationEntryVisitor

            // Resolve the candidate annotation entry. If it is an `@OptIn`/`@UseExperimental` annotation,
            // resolve all expected experimental markers.
            val resolutionFacade = annotationEntry.getResolutionFacade()
            val annotationContext = annotationEntry.analyze(resolutionFacade)
            val annotationFqName = annotationContext[BindingContext.ANNOTATION, annotationEntry]?.fqName
            if (annotationFqName !in OptInNames.USE_EXPERIMENTAL_FQ_NAMES) return@annotationEntryVisitor

            val resolvedMarkers = mutableListOf<ResolvedMarker>()
            for (arg in annotationEntry.valueArguments) {
                val argumentExpression = arg.getArgumentExpression()?.safeAs<KtClassLiteralExpression>() ?: continue
                val markerFqName = annotationContext[
                        BindingContext.REFERENCE_TARGET,
                        argumentExpression.lhs?.safeAs<KtNameReferenceExpression>()
                ]?.fqNameSafe ?: continue
                resolvedMarkers.add(ResolvedMarker(argumentExpression.createSmartPointer(), markerFqName))
            }

            // Find the scope of the `@OptIn` declaration and collect all its experimental markers.
            val markerProcessor = MarkerCollector(resolutionFacade)
            annotationEntry.getOwner()?.accept(OptInMarkerVisitor(), markerProcessor)

            val unusedMarkers = resolvedMarkers.filter { markerProcessor.isUnused(it.fqName) }
            if (annotationEntry.valueArguments.size == unusedMarkers.size) {
                // If all markers in the `@OptIn` annotation are useless, create a quick fix to remove
                // the entire annotation.
                holder.registerProblem(
                    annotationEntry,
                    KotlinBundle.message("inspection.unnecessary.opt_in.redundant.annotation"),
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    RemoveAnnotationEntry()
                )
            } else {
                // Check each resolved marker whether it is actually used in the scope of the `@OptIn`.
                // Create a quick fix to remove the unnecessary marker if no marked names have been found.
                for (marker in unusedMarkers) {
                    val expression = marker.expression.element ?: continue
                    holder.registerProblem(
                        expression,
                        KotlinBundle.message(
                                    "inspection.unnecessary.opt_in.redundant.marker",
                                    marker.fqName.shortName().render()
                                ),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        RemoveAnnotationArgumentOrEntireEntry()
                    )
                }
            }
        }
    }
}

/**
 * A processor that collects experimental markers referred by names in the `@OptIn` annotation scope.
 */
private class MarkerCollector(private val resolutionFacade: ResolutionFacade) {
    // Experimental markers found during a check for a specific annotation entry
    private val foundMarkers = mutableSetOf<FqName>()

    // A checker instance for setter call detection
    private val readWriteAccessChecker = ReadWriteAccessChecker.getInstance()

    /**
     * Check if a specific experimental marker is not used in the scope of a specific `@OptIn` annotation.
     *
     * @param marker the fully qualified name of the experimental marker of interest
     * @return true if no marked names was found during the check, false if there is at least one marked name
     */
    fun isUnused(marker: FqName): Boolean = marker !in foundMarkers

    /**
     * Collect experimental markers for a declaration and add them to [foundMarkers].
     *
     * The `@OptIn` annotation is useful for declarations that override a marked declaration (e.g., overridden
     * functions or properties in classes/objects). If the declaration overrides another name, we should
     * collect experimental markers from the overridden declaration.
     *
     * @param declaration the declaration to process
     */
    fun collectMarkers(declaration: KtDeclaration?) {
        if (declaration == null) return
        if (declaration !is KtFunction && declaration !is KtProperty && declaration !is KtParameter) return
        if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            val descriptor = declaration.resolveToDescriptorIfAny(resolutionFacade)?.safeAs<CallableMemberDescriptor>() ?: return
            descriptor.getDirectlyOverriddenDeclarations().forEach { it.collectMarkers(declaration.module) }
        }
    }

    /**
     * Collect experimental markers for an expression and add them to [foundMarkers].
     *
     * @param expression the expression to process
     */
    fun collectMarkers(expression: KtReferenceExpression?) {
        if (expression == null) return

        // Resolve the reference to descriptors, then analyze the annotations
        // For each descriptor, we also check a corresponding importable descriptor
        // if it is not equal to the descriptor itself. The goal is to correctly
        // resolve class names. For example, the `Foo` reference in the code fragment
        // `val x = Foo()` is resolved as a constructor, while the corresponding
        // class descriptor can be found as the constructor's importable name.
        // Both constructor and class may be annotated with an experimental API marker,
        // so we should check both of them.
        val descriptorList = expression
            .resolveMainReferenceToDescriptors()
            .flatMap { setOf(it, it.getImportableDescriptor()) }

        val module = expression.module
        for (descriptor in descriptorList) {
            descriptor.collectMarkers(module)
            // A special case: a property has no experimental markers but its setter is experimental.
            // We need to additionally collect markers from the setter if it is invoked in the expression.
            if (descriptor is PropertyDescriptor) {
                val setter = descriptor.setter ?: continue
                if (expression.isSetterCall()) setter.collectMarkers(module)
            }
        }
    }

    /**
     * Actually collect markers for a resolved descriptor and add them to [foundMarkers].
     *
     * @receiver the descriptor to collect markers
     */
    private fun DeclarationDescriptor.collectMarkers(currentModule: Module?) {
        for (ann in annotations) {
            val annotationFqName = ann.fqName ?: continue
            val annotationClass = ann.annotationClass ?: continue

            // Add the annotation class as a marker if it has `@RequireOptIn` annotation.
            if (annotationClass.annotations.hasAnnotation(OptInNames.REQUIRES_OPT_IN_FQ_NAME)
                || annotationClass.annotations.hasAnnotation(OptInNames.OLD_EXPERIMENTAL_FQ_NAME)) {
                foundMarkers += annotationFqName
            }

            val wasExperimental = annotations.findAnnotation(OptInNames.WAS_EXPERIMENTAL_FQ_NAME) ?: continue
            val sinceKotlin = annotations.findAnnotation(SINCE_KOTLIN_FQ_NAME) ?: continue
            // If there are both `@SinceKotlin` and `@WasExperimental` annotations,
            // and Kotlin API version of the module is less than the version specified by `@SinceKotlin`,
            // then the `@OptIn` for `@WasExperimental` marker is necessary and should be added
            // to the set of found markers.
            //
            // For example, consider a function
            // ```
            // @SinceKotlin("1.6")
            // @WasExperimental(Marker::class)
            // fun foo() { ... }
            // ```
            // This combination of annotations means that `foo` was experimental before Kotlin 1.6
            // and required `@OptIn(Marker::class) or `@Marker` annotation. When the client code
            // is compiled as Kotlin 1.6 code, there are no problems, and the `@OptIn(Marker::class)`
            // annotation would not be necessary. At the same time, when the code is compiled with
            // `apiVersion = 1.5`, the non-experimental declaration of `foo` will be hidden
            // from the resolver, so `@OptIn` is necessary for the code to compile.

            val sinceKotlinApiVersion = sinceKotlin.allValueArguments[VERSION_ARGUMENT]
                ?.safeAs<StringValue>()?.value?.let {
                    LanguageVersion.fromVersionString(it)
                }

            val moduleApiVersion = currentModule?.let {
                KotlinFacetSettingsProvider.getInstance(it.project)?.getSettings(it)?.apiLevel
            }

            // If the Kotlin API version of the current module could not be found,
            // add the marker, as it is better to skip a redundant `@OptIn` than to remove
            // a necessary annotation. If the module API version is known, compare it
            // to the API version specified via the `@SinceKotlin` annotation.
            // Add the marker only if the module API version is less than the version
            // required by the declaration.
            if (moduleApiVersion == null || (sinceKotlinApiVersion != null && moduleApiVersion < sinceKotlinApiVersion)) {
                wasExperimental.allValueArguments[OptInNames.WAS_EXPERIMENTAL_ANNOTATION_CLASS]?.safeAs<ArrayValue>()?.value
                    ?.mapNotNull { it.safeAs<KClassValue>()?.getArgumentType(module)?.fqName }
                    ?.forEach { foundMarkers.add(it) }
            }
        }
    }

    /**
     * Check if the reference expression is a part of a property setter invocation.
     *
     * @receiver the expression to check
     */
    private fun KtReferenceExpression.isSetterCall(): Boolean =
        readWriteAccessChecker.readWriteAccessWithFullExpression(this, true).first.isWrite

    private val VERSION_ARGUMENT = Name.identifier("version")
}

/**
 * The marker collecting visitor that navigates the PSI tree in the scope of the `@OptIn` declaration
 * and collects experimental markers.
 */
private class OptInMarkerVisitor : KtTreeVisitor<MarkerCollector>() {
    override fun visitNamedDeclaration(declaration: KtNamedDeclaration, markerCollector: MarkerCollector): Void? {
        markerCollector.collectMarkers(declaration)
        return super.visitNamedDeclaration(declaration, markerCollector)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression, markerCollector: MarkerCollector): Void? {
        markerCollector.collectMarkers(expression)
        return super.visitReferenceExpression(expression, markerCollector)
    }
}

/**
 * A quick fix that removes the argument from the value argument list of an annotation entry,
 * or the entire entry if the argument was the only argument of the annotation.
 */
private class RemoveAnnotationArgumentOrEntireEntry : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.unnecessary.opt_in.remove.marker.fix.family.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val valueArgument = descriptor.psiElement?.parentOfType<KtValueArgument>() ?: return
        val annotationEntry = valueArgument.parentOfType<KtAnnotationEntry>() ?: return
        if (annotationEntry.valueArguments.size == 1) {
            annotationEntry.delete()
        } else {
            annotationEntry.valueArgumentList?.removeArgument(valueArgument)
        }
    }
}

/**
 * A quick fix that removes the entire annotation entry.
 */
private class RemoveAnnotationEntry : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.unnecessary.opt_in.remove.annotation.fix.family.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val annotationEntry = descriptor.psiElement?.safeAs<KtAnnotationEntry>() ?: return
        annotationEntry.delete()
    }
}
