// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.components.evaluateAsAnnotationValue
import org.jetbrains.kotlin.analysis.api.components.isClassType
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
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
 * annotated with `@X` where [annotationClassId] is the [ClassId] of X.
 *
 * This is K2 implementation which is very similar to [org.jetbrains.kotlin.resolve.checkers.OptInUsageChecker.Companion.isOptInAllowed].
 * One difference is that check of [SubclassOptInRequired] is not implemented here since it is not needed for current method usages.
 */
context(_: KaSession)
fun KtElement.isOptInAllowed(annotationClassId: ClassId): Boolean {
    if (annotationClassId.asFqNameString() in languageVersionSettings.getFlag(AnalysisFlags.optIn)) return true

    return parentsWithSelf.any {
        it.isDeclarationAnnotatedWith(annotationClassId) ||
                it.isElementAnnotatedWithOptIn(annotationClassId)
    }
}

context(_: KaSession)
private fun PsiElement.isDeclarationAnnotatedWith(annotationClassId: ClassId): Boolean =
    this is KtDeclaration && symbol.annotations.contains(annotationClassId)

context(_: KaSession)
private fun KtAnnotationEntry.getAnnotatedOptIns(): Set<ClassId> {
    if (typeReference?.type?.isClassType(OptInNames.OPT_IN_CLASS_ID) != true) return emptySet()

    return valueArguments.mapNotNull { it.getAnnotationClassValue() }.toSet()
}

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
private fun ValueArgument.getAnnotationClassValue(): ClassId? =
    getArgumentExpression()
        ?.evaluateAsAnnotationValue()
        ?.safeAs<KaAnnotationValue.ClassLiteralValue>()
        ?.classId

context(_: KaSession)
private fun PsiElement.isElementAnnotatedWithOptIn(annotationClassId: ClassId): Boolean =
    this is KtAnnotated && annotationEntries.any { annotationClassId in it.getAnnotatedOptIns() }

context(_: KaSession)
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

context(_: KaSession)
fun KaAnnotatedSymbol.isOptInRequired(annotationClassId: ClassId, moduleApiVersion: ApiVersion) =
    annotationClassId in getRequiredOptIns(setOf(annotationClassId), moduleApiVersion)

context(_: KaSession)
fun KtElement.isOptInSatisfied(symbol: KaAnnotatedSymbol, annotationClassId: ClassId): Boolean =
    !symbol.isOptInRequired(annotationClassId, languageVersionSettings.apiVersion)
            || isOptInAllowed(annotationClassId)