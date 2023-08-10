// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render

object AnnotationModificationHelper {
    fun addAnnotation(
        element: KtModifierListOwner,
        annotationFqName: FqName,
        annotationInnerText: String?,
        useSiteTarget: AnnotationUseSiteTarget?,
        searchForExistingEntry: (KtAnnotated) -> KtAnnotationEntry?,
        whiteSpaceText: String,
        addToExistingAnnotation: ((KtAnnotationEntry) -> Boolean)?
    ): Boolean {
        val annotationText = buildAnnotationText(annotationFqName, annotationInnerText, useSiteTarget)

        val psiFactory = KtPsiFactory(element.project)
        val modifierList = element.modifierList

        if (modifierList == null) {
            val addedAnnotation = element.addAnnotationEntry(psiFactory.createAnnotationEntry(annotationText))
            ShortenReferencesFacility.getInstance().shorten(addedAnnotation)
            if (element !is KtScriptInitializer) return true

            val addedModifierList = addedAnnotation.parent as? KtModifierList ?: return false
            val newLine = psiFactory.createNewLine()
            addedModifierList.addAfter(newLine, addedAnnotation)
            return true
        }

        val entry = searchForExistingEntry(element)
        if (entry == null) {
            // no annotation
            val newAnnotation = psiFactory.createAnnotationEntry(annotationText)
            val addedAnnotation = modifierList.addBefore(newAnnotation, modifierList.firstChild) as KtElement
            val whiteSpace = psiFactory.createWhiteSpace(whiteSpaceText)
            modifierList.addAfter(whiteSpace, addedAnnotation)

            ShortenReferencesFacility.getInstance().shorten(addedAnnotation)
            return true
        }

        if (addToExistingAnnotation != null) {
            return addToExistingAnnotation(entry)
        }

        return false
    }

    fun buildAnnotationText(fqName: FqName, annotationInnerText: String? = null, useSiteTarget: AnnotationUseSiteTarget? = null): String =
        buildString {
            append('@')
            if (useSiteTarget != null) append("${useSiteTarget.renderName}:")
            append(fqName.render())
            if (annotationInnerText != null) append("($annotationInnerText)")
        }

}