// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*

/**
 * Add a new annotation to the declaration or expression, or modify an existing annotation. Uses [analyze].
 *
 * Note: if the search for an existing annotation is enabled (this is default for compatibility), the function will
 * resolve annotation names. This should be avoided if the function is called from an event dispatcher thread (EDT),
 * e.g., if it is a part of a write action such as the `invoke()` function of a quick fix.
 *
 * @receiver the annotation owner to modify
 * @param annotationClassId fully qualified name of the annotation to add
 * @param annotationInnerText the inner text (rendered arguments) of the annotation, or null if there is no inner text (default)
 * @param useSiteTarget the use site target of the annotation, or null if no explicit use site target is provided
 * @param searchForExistingEntry `true` if the function should search for an existing annotation and update it instead of creating a new entry
 * @param whiteSpaceText the whitespace separator that should be inserted between annotation entries (newline by default)
 * @param addToExistingAnnotation a lambda expression to run on an existing annotation if it has been found
 * @return `true` if an annotation has been added or modified, `false` otherwise
 */
fun KtModifierListOwner.addAnnotation(
    annotationClassId: ClassId,
    annotationInnerText: String? = null,
    useSiteTarget: AnnotationUseSiteTarget? = null,
    searchForExistingEntry: Boolean = true,
    whiteSpaceText: String = "\n",
    addToExistingAnnotation: ((KtAnnotationEntry) -> Boolean)? = null
): Boolean {
    val searchForExistingEntryFn: (KtAnnotated) -> KtAnnotationEntry? =
        { annotated -> if (searchForExistingEntry) annotated.findAnnotation(annotationClassId, useSiteTarget) else null }

    return AnnotationModificationHelper.addAnnotation(
        this,
        annotationClassId.asSingleFqName(),
        annotationInnerText,
        useSiteTarget,
        searchForExistingEntryFn,
        whiteSpaceText,
        addToExistingAnnotation
    )
}

fun KtElement.addAnnotation(annotationClassId: ClassId, annotationInnerText: String? = null, searchForExistingEntry: Boolean) {
    when (this) {
        is KtModifierListOwner -> addAnnotation(annotationClassId, annotationInnerText, searchForExistingEntry = searchForExistingEntry)
        else -> {
            val annotationText = AnnotationModificationHelper.buildAnnotationText(annotationClassId.asSingleFqName(), annotationInnerText)

            val placeholderText = "ORIGINAL_ELEMENT_WILL_BE_INSERTED_HERE"
            val annotatedExpression = KtPsiFactory(project).createExpression(annotationText + "\n" + placeholderText)

            val copy = this.copy()

            val afterReplace = this.replace(annotatedExpression) as KtAnnotatedExpression
            val annotationEntry = afterReplace.annotationEntries.first()
            val toReplace = afterReplace.findElementAt(afterReplace.textLength)!!
            check(toReplace.text == placeholderText)
            toReplace.replace(copy)
            ShortenReferencesFacility.getInstance().shorten(annotationEntry)
        }
    }
}


fun KtAnnotated.findAnnotation(
    annotationClassId: ClassId,
    useSiteTarget: AnnotationUseSiteTarget? = null,
    withResolve: Boolean = false
): KtAnnotationEntry? {
    val annotationEntry = KotlinPsiHeuristics.findAnnotation(this, annotationClassId.asSingleFqName(), useSiteTarget)
    return annotationEntry?.takeIf { isAnnotationWithClassId(it, annotationClassId, withResolve) }
}

fun KtFileAnnotationList.findAnnotation(
    annotationClassId: ClassId,
    useSiteTarget: AnnotationUseSiteTarget? = null,
    withResolve: Boolean = false
): KtAnnotationEntry? {
    val annotationEntry = KotlinPsiHeuristics.findAnnotation(this, annotationClassId.asSingleFqName(), useSiteTarget)
    return annotationEntry?.takeIf { isAnnotationWithClassId(it, annotationClassId, withResolve) }
}

private fun isAnnotationWithClassId(entry: KtAnnotationEntry, classId: ClassId, withResolve: Boolean): Boolean {
    if (entry.shortName != classId.shortClassName) return false
    if (!withResolve) return true
    return analyze(entry) {
        entry.typeReference?.getKtType()?.isClassTypeWithClassId(classId) == true
    }
}