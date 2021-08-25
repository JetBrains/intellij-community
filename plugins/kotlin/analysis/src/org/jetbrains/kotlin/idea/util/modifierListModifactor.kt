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

fun KtModifierListOwner.addAnnotation(
    annotationFqName: FqName,
    annotationInnerText: String? = null,
    whiteSpaceText: String = "\n",
    useSiteTarget: AnnotationUseSiteTarget? = null,
    addToExistingAnnotation: ((KtAnnotationEntry) -> Boolean)? = null
): Boolean {
    val useSiteTargetPrefix = if (useSiteTarget != null) "${useSiteTarget.renderName}:" else ""
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

    val entry = findAnnotation(annotationFqName)
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
