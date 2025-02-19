// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.WasExperimentalOptInsNecessityChecker
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Checks whether there's an element lexically above in the tree, annotated with @[OptIn]`(X::class)`, or a declaration
 * annotated with `@X` where [optInMarker] is the [ClassId] of X.
 *
 * This is K2 implementation which is very similar to [org.jetbrains.kotlin.resolve.checkers.OptInUsageChecker.Companion.isOptInAllowed].
 * One difference is that check of [SubclassOptInRequired] is not implemented here since it is not needed for current method usages.
 */
context(KaSession)
fun KtElement.isOptInAllowed(optInMarker: ClassId): Boolean {
    if (optInMarker.asFqNameString() in languageVersionSettings.getFlag(AnalysisFlags.optIn)) return true

    return parentsWithSelf.any {
        it.isDeclarationAnnotatedWith(optInMarker) ||
                it.isElementAnnotatedWithOptIn(optInMarker)
    }
}

context(KaSession)
private fun PsiElement.isDeclarationAnnotatedWith(optInMarker: ClassId): Boolean =
    this is KtDeclaration && symbol.annotations.contains(optInMarker)

context(KaSession)
private fun KtAnnotationEntry.getAnnotatedOptIns(): Set<ClassId> {
    if (typeReference?.type?.isClassType(OptInNames.OPT_IN_CLASS_ID) != true) return emptySet()

    return valueArguments.mapNotNull {
        (it.getArgumentExpression() as? KtClassLiteralExpression)?.classLiteralId
    }.toSet()
}

context(KaSession)
val KtClassLiteralExpression.classLiteralId: ClassId?
    get() = (receiverType as? KaClassType)?.classId

context(KaSession)
private fun PsiElement.isElementAnnotatedWithOptIn(optInMarker: ClassId): Boolean =
    this is KtAnnotated && annotationEntries.any { optInMarker in it.getAnnotatedOptIns() }

context(KaSession)
private fun KaClassLikeSymbol.getSubclassOptInRequiredMarkers(): Set<ClassId> =
    annotations[OptInNames.SUBCLASS_OPT_IN_REQUIRED_CLASS_ID].flatMapTo(mutableSetOf()) { anno ->
        anno.arguments.flatMap { arg ->
            val expression = arg.expression
            if (expression is KaAnnotationValue.ArrayValue && arg.name == OptInNames.OPT_IN_ANNOTATION_CLASS) {
                expression.values.mapNotNull {
                    (it as? KaAnnotationValue.ClassLiteralValue)?.classId
                }
            } else {
                emptyList()
            }
        }
    }

/**
 * Check the opt-in markers directly required to use the given symbol.
 *
 * This does not include any opt-ins necessary surrounding the use of the symbol, such as opt-ins
 * for type parameters, value parameter types, receiver types, return types, and so on.
 *
 * @receiver The symbol for the type or callable being used.
 * @param possibleOptIns The universe of possible opt-ins to check.
 * @param moduleApiVersion The Kotlin version in use at the use site, for [WasExperimental] handling.
 */
context(KaSession)
fun KaAnnotatedSymbol.getRequiredOptIns(possibleOptIns: Set<ClassId>, moduleApiVersion: ApiVersion): Set<ClassId> {
    val requiredOptIns = annotations.classIds.toMutableSet()

    // Early exit - every opt-in we're looking for is already annotated on this symbol, so no need to
    // look any further.
    if (requiredOptIns.containsAll(possibleOptIns)) return possibleOptIns

    // We've checked the actual annotations on this element, now check any opt-ins that _were_ required
    // for an old language version we're using.
    requiredOptIns.addAll(
        WasExperimentalOptInsNecessityChecker.getNecessaryOptInsFromWasExperimental(annotations, moduleApiVersion))

    // Filter down to the annotations we're concerned about.
    requiredOptIns.retainAll(possibleOptIns)

    return requiredOptIns
}

context(KaSession)
fun KaClassLikeSymbol.getRequiredOptInsToSubclass(
    possibleOptIns: Set<ClassId>,
): Set<ClassId> {
    return getSubclassOptInRequiredMarkers() intersect possibleOptIns
}

context(KaSession)
fun KaAnnotatedSymbol.isOptInRequired(annotationClassId: ClassId, moduleApiVersion: ApiVersion) =
    annotationClassId in getRequiredOptIns(setOf(annotationClassId), moduleApiVersion)

context(KaSession)
fun KtElement.isOptInSatisfied(symbol: KaAnnotatedSymbol, annotationClassId: ClassId): Boolean =
    !symbol.isOptInRequired(annotationClassId, languageVersionSettings.apiVersion)
            || isOptInAllowed(annotationClassId)