// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.replaceFileAnnotationList
import org.jetbrains.kotlin.renderer.render

/**
 * A quick fix to add file-level annotations, e.g. `@file:OptIn(SomeExperimentalAnnotation::class)`.
 *
 * The fix either adds the argument to an existing annotation entry found via `annotationFinder`,
 * or creates a new annotation if no entry is found.
 *
 * @param element the file where the annotation should be added
 * @param annotationFqName the fully qualified name of the annotation class (e.g., `kotlin.OptIn`)
 * @param argumentClassFqName the fully qualified name of the argument class (e.g., `SomeExperimentalAnnotation`) (optional)
 * @param annotationFinder function that locates an existing annotation entry in the file, if present (optional)
 */
open class AddFileAnnotationFix(
    element: KtFile,
    private val annotationFqName: FqName,
    private val argumentClassFqName: FqName? = null,
    private val annotationFinder: (KtFile, FqName) -> KtAnnotationEntry? = { _, _ -> null },
) : PsiUpdateModCommandAction<KtFile>(element) {
    override fun getPresentation(context: ActionContext, element: KtFile): Presentation {
        val annotationName = annotationFqName.shortName().asString()
        val innerText = argumentClassFqName?.shortName()?.asString()?.let { "$it::class" } ?: ""
        val annotationText = "$annotationName($innerText)"
        val actionName = KotlinBundle.message("fix.add.annotation.text.containing.file", annotationText, element.name)
        return Presentation.of(actionName)
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.annotation.family")

    override fun invoke(
        context: ActionContext,
        element: KtFile,
        updater: ModPsiUpdater,
    ) {
        val innerText = argumentClassFqName?.render()?.let { "$it::class" }
        val annotationText = when (innerText) {
            null -> annotationFqName.render()
            else -> "${annotationFqName.render()}($innerText)"
        }

        val psiFactory = KtPsiFactory(context.project)
        val annotationList = element.fileAnnotationList
        if (annotationList == null) {
            // If there are no existing file-level annotations, create an annotation list with the new annotation
            val newAnnotationList = psiFactory.createFileAnnotationListWithAnnotation(annotationText)
            val createdAnnotationList = replaceFileAnnotationList(element, newAnnotationList)
            element.addAfter(psiFactory.createWhiteSpace("\n"), createdAnnotationList)
            ShortenReferencesFacility.getInstance().shorten(createdAnnotationList)
        } else {
            val existingAnnotationEntry = annotationFinder.invoke(element, annotationFqName)
            if (existingAnnotationEntry == null) {
                // There are file-level annotations, but the fix is expected to add a new entry
                val newAnnotation = psiFactory.createFileAnnotation(annotationText)
                annotationList.add(psiFactory.createWhiteSpace("\n"))
                annotationList.add(newAnnotation)
                ShortenReferencesFacility.getInstance().shorten(annotationList)
            } else if (innerText != null) {
                // There is an existing annotation and the non-null argument that should be added to it
                addArgumentToExistingAnnotation(existingAnnotationEntry, innerText)
            }
        }
    }

    /**
     * Add an argument to the existing annotation.
     *
     * @param annotationEntry the existing annotation entry
     * @param argumentText the argument text
     */
    private fun addArgumentToExistingAnnotation(annotationEntry: KtAnnotationEntry, argumentText: String) {
        val existingArgumentList = annotationEntry.valueArgumentList
        val psiFactory = KtPsiFactory(annotationEntry.project)
        val newArgumentList = psiFactory.createCallArguments("($argumentText)")
        when {
            existingArgumentList == null -> // use the new argument list
                annotationEntry.addAfter(newArgumentList, annotationEntry.lastChild)
            existingArgumentList.arguments.isEmpty() -> // replace '()' with the new argument list
                existingArgumentList.replace(newArgumentList)
            else -> // add the new argument to the existing list
                existingArgumentList.addArgument(newArgumentList.arguments[0])
        }
        ShortenReferencesFacility.getInstance().shorten(annotationEntry)
    }
}


/**
 * A specialized version of [AddFileAnnotationFix] that adds @OptIn(...) annotations to the containing file.
 *
 * This class reuses the parent's [invoke] method, but overrides the [getPresentation] method to provide
 * more descriptive opt-in related messages.
 *
 * TODO: migrate from FqName to ClassId fully when the K1 plugin is dropped.
 *
 * @param element the file there the annotation should be added
 * @param optInFqName name of OptIn annotation
 * @param argumentClassFqName the fully qualified name of the annotation to opt-in
 * @param annotationFinder function that locates an existing annotation entry in the file, if present (optional)
 */
class UseOptInFileAnnotationFix(
    element: KtFile,
    optInFqName: FqName,
    annotationFinder: (file: KtFile, annotationFqName: FqName) -> KtAnnotationEntry?,
    private val argumentClassFqName: FqName
) : AddFileAnnotationFix(element, optInFqName, argumentClassFqName, annotationFinder) {

    override fun getPresentation(context: ActionContext, element: KtFile): Presentation {
        val argumentText = argumentClassFqName.shortName().asString()
        val actionName = KotlinBundle.message("fix.opt_in.text.use.containing.file", argumentText, element.name)
        return Presentation.of(actionName)
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")
}