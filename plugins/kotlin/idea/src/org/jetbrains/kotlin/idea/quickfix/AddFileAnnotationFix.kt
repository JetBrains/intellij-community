// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.replaceFileAnnotationList
import org.jetbrains.kotlin.renderer.render

/**
 * A quick fix to add file-level annotations, e.g. `@file:OptIn(SomeExperimentalAnnotation::class)`.
 *
 * The fix either creates a new annotation or adds the argument to the existing annotation entry.
 * It does not check whether the annotation class allows duplicating annotations; it is the caller responsibility.
 * For example, only one `@file:OptIn(...)` annotation is allowed, so if this annotation entry already exists,
 * the caller should pass the non-null smart pointer to it as the `existingAnnotationEntry` argument.
 *
 * @param file the file where the annotation should be added
 * @param annotationFqName the fully qualified name of the annotation class (e.g., `kotlin.OptIn`)
 * @param argumentClassFqName the fully qualified name of the argument class (e.g., `SomeExperimentalAnnotation`) (optional)
 * @param existingAnnotationEntry a smart pointer to the existing annotation entry with the same annotation class (optional)
 */
open class AddFileAnnotationFix(
    file: KtFile,
    private val annotationFqName: FqName,
    private val argumentClassFqName: FqName? = null,
    private val existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
) : KotlinQuickFixAction<KtFile>(file) {

    override fun getText(): String {
        val annotationName = annotationFqName.shortName().asString()
        val innerText = argumentClassFqName?.shortName()?.asString()?.let { "$it::class" } ?: ""
        val annotationText = "$annotationName($innerText)"
        return KotlinBundle.message("fix.add.annotation.text.containing.file", annotationText, element?.name ?: "")
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.annotation.family")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val fileToAnnotate = element ?: return
        val innerText = argumentClassFqName?.render()?.let { "$it::class"}
        val annotationText = when (innerText) {
            null -> annotationFqName.render()
            else -> "${annotationFqName.render()}($innerText)"
        }

        val psiFactory = KtPsiFactory(fileToAnnotate)
        if (fileToAnnotate.fileAnnotationList == null) {
            // If there are no existing file-level annotations, create an annotation list with the new annotation
            val newAnnotationList = psiFactory.createFileAnnotationListWithAnnotation(annotationText)
            val createdAnnotationList = replaceFileAnnotationList(fileToAnnotate, newAnnotationList)
            fileToAnnotate.addAfter(psiFactory.createWhiteSpace("\n"), createdAnnotationList)
            ShortenReferences.DEFAULT.process(createdAnnotationList)
        } else {
            val annotationList = fileToAnnotate.fileAnnotationList ?: return
            if (existingAnnotationEntry == null) {
                // There are file-level annotations, but the fix is expected to add a new entry
                val newAnnotation = psiFactory.createFileAnnotation(annotationText)
                annotationList.add(psiFactory.createWhiteSpace("\n"))
                annotationList.add(newAnnotation)
                ShortenReferences.DEFAULT.process(annotationList)
            } else if (innerText != null) {
                // There is an existing annotation and the non-null argument that should be added to it
                addArgumentToExistingAnnotation(existingAnnotationEntry, innerText)
            }
        }
    }

    /**
     * Add an argument to the existing annotation.
     *
     * @param annotationEntry a smart pointer to the existing annotation entry
     * @param argumentText the argument text
     */
    private fun addArgumentToExistingAnnotation(annotationEntry: SmartPsiElementPointer<KtAnnotationEntry>, argumentText: String) {
        val entry = annotationEntry.element ?: return
        val existingArgumentList = entry.valueArgumentList
        val psiFactory = KtPsiFactory(entry)
        val newArgumentList = psiFactory.createCallArguments("($argumentText)")
        when {
            existingArgumentList == null -> // use the new argument list
                entry.addAfter(newArgumentList, entry.lastChild)
            existingArgumentList.arguments.isEmpty() -> // replace '()' with the new argument list
                existingArgumentList.replace(newArgumentList)
            else -> // add the new argument to the existing list
                existingArgumentList.addArgument(newArgumentList.arguments[0])
        }
        ShortenReferences.DEFAULT.process(entry)
    }
}
