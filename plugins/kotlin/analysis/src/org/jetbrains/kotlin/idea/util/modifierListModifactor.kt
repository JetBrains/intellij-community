// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * Add a new annotation to the declaration or expression, or modify an existing annotation. A compatibility overload.
 *
 * @see [org.jetbrains.kotlin.idea.util.ModifierListModifactorKt.addAnnotation(org.jetbrains.kotlin.psi.KtModifierListOwner, org.jetbrains.kotlin.name.FqName, java.lang.String, org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget, boolean, java.lang.String, kotlin.jvm.functions.Function1<? super org.jetbrains.kotlin.psi.KtAnnotationEntry,java.lang.Boolean>)]
 */
fun KtModifierListOwner.addAnnotation(
    annotationFqName: FqName,
    annotationInnerText: String? = null,
    whiteSpaceText: String = "\n",
    addToExistingAnnotation: ((KtAnnotationEntry) -> Boolean)? = null
): Boolean {
    return addAnnotation(annotationFqName, annotationInnerText, null, true, whiteSpaceText, addToExistingAnnotation)
}

/**
 * Add a new annotation to the declaration or expression, or modify an existing annotation. A compatibility overload.
 *
 * @see [org.jetbrains.kotlin.idea.util.ModifierListModifactorKt.addAnnotation(org.jetbrains.kotlin.psi.KtModifierListOwner, org.jetbrains.kotlin.name.FqName, java.lang.String, org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget, boolean, java.lang.String, kotlin.jvm.functions.Function1<? super org.jetbrains.kotlin.psi.KtAnnotationEntry,java.lang.Boolean>)]
 */
fun KtModifierListOwner.addAnnotation(
    annotationFqName: FqName,
    annotationInnerText: String? = null,
    useSiteTarget: AnnotationUseSiteTarget?,
    whiteSpaceText: String = "\n",
    addToExistingAnnotation: ((KtAnnotationEntry) -> Boolean)? = null
): Boolean {
    return addAnnotation(annotationFqName, annotationInnerText, useSiteTarget, true, whiteSpaceText, addToExistingAnnotation)
}

/**
 * Add a new annotation to the declaration or expression, or modify an existing annotation.
 *
 * Note: if the search for and existing annotation is enabled (this is default for compatibility), the function will
 * resolve annotation names. This should be avoided is the function is called from an event dispatcher thread (EDT),
 * e.g., if it is a part of a write action such as the `invoke()` function of a quick fix.
 *
 * @receiver the annotation owner to modify
 * @param annotationFqName fully qualified name of the annotation to add
 * @param annotationInnerText the inner text (rendered arguments) of the annotation, or null if there is no inner text (default)
 * @param useSiteTarget the use site target of the annotation, or null if no explicit use site target is provided
 * @param searchForExistingEntry `true` iff the function should search for an existing annotation and update it instead of creating a new entry
 * @param whiteSpaceText the whitespace separator that should be inserted between annotation entries (newline by default)
 * @param addToExistingAnnotation a lambda expression to run on an existing annotation if it has been found
 * @return `true` if an annotation has been added or modified, `false` otherwise
 */
fun KtModifierListOwner.addAnnotation(
    annotationFqName: FqName,
    annotationInnerText: String? = null,
    useSiteTarget: AnnotationUseSiteTarget?,
    searchForExistingEntry: Boolean = true,
    whiteSpaceText: String = "\n",
    addToExistingAnnotation: ((KtAnnotationEntry) -> Boolean)? = null
): Boolean {
    val useSiteTargetPrefix = useSiteTarget?.let { "${it.renderName}:" } ?: ""
    val annotationText = when (annotationInnerText) {
        null -> "@${useSiteTargetPrefix}${annotationFqName.render()}"
        else -> "@${useSiteTargetPrefix}${annotationFqName.render()}($annotationInnerText)"
    }

    val psiFactory = KtPsiFactory(this)
    val modifierList = modifierList

    if (modifierList == null) {
        val addedAnnotation = addAnnotationEntry(psiFactory.createAnnotationEntry(annotationText))
        ShortenReferences.DEFAULT.process(addedAnnotation)
        return true
    }

    val entry = if (searchForExistingEntry) findAnnotation(annotationFqName) else null
    if (entry == null) {
        // no annotation
        val newAnnotation = psiFactory.createAnnotationEntry(annotationText)
        val addedAnnotation = modifierList.addBefore(newAnnotation, modifierList.firstChild) as KtElement
        val whiteSpace = psiFactory.createWhiteSpace(whiteSpaceText)
        modifierList.addAfter(whiteSpace, addedAnnotation)

        ShortenReferences.DEFAULT.process(addedAnnotation)
        return true
    }

    if (addToExistingAnnotation != null) {
        return addToExistingAnnotation(entry)
    }

    return false
}

fun KtAnnotated.findAnnotation(annotationFqName: FqName): KtAnnotationEntry? {
    if (annotationEntries.isEmpty()) return null

    val context = analyze(bodyResolveMode = BodyResolveMode.PARTIAL)
    val descriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, this] ?: return null

    // Make sure all annotations are resolved
    descriptor.annotations.toList()

    return annotationEntries.firstOrNull { entry -> context.get(BindingContext.ANNOTATION, entry)?.fqName == annotationFqName }
}

fun KtAnnotated.hasJvmFieldAnnotation(): Boolean = findAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) != null
