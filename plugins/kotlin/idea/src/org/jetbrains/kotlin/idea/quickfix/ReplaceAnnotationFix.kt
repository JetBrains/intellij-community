// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.renderer.render

/**
 * The fix that removes the existing annotation and appends the new annotation to the same element.
 *
 * @param annotationEntry the annotation entry to remove
 * @param annotationFqName the fully qualified name of the new annotation class
 * @param argumentClassFqName the fully qualified name of the annotation argument
 * @param useSiteTarget
 */
open class ReplaceAnnotationFix(
    annotationEntry: KtAnnotationEntry,
    private val modifierListOwner: SmartPsiElementPointer<KtModifierListOwner>,
    private val annotationFqName: FqName,
    private val argumentClassFqName: FqName? = null,
    private val useSiteTarget: AnnotationUseSiteTarget? = null,
    private val existingReplacementAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
) : KotlinQuickFixAction<KtAnnotationEntry>(annotationEntry) {
    protected fun renderAnnotationText(): String {
        val useSiteTargetPrefix = if (useSiteTarget != null) "${useSiteTarget.renderName}:" else ""
        val annotationShortName = annotationFqName.shortName().render()
        return when (val annotationInnerText = argumentClassFqName?.let { "${it.shortName().render()}::class"}) {
            null -> "@${useSiteTargetPrefix}${annotationShortName}"
            else -> "@${useSiteTargetPrefix}${annotationShortName}($annotationInnerText)"
        }
    }
    override fun getText(): String {
        return KotlinBundle.message("fix.replace.annotation.text", renderAnnotationText())
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.replace.annotation.family")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.delete()
        val existingEntry = existingReplacementAnnotationEntry?.element
        val owner = modifierListOwner.element ?: return
        val annotationInnerText = argumentClassFqName?.let { "${it.render()}::class"}
        if (existingEntry != null) {
            if (annotationInnerText == null) return
            val psiFactory = KtPsiFactory(existingEntry)
            existingEntry.valueArgumentList?.addArgument(psiFactory.createArgument(annotationInnerText))
                ?: existingEntry.addAfter(psiFactory.createCallArguments("($annotationInnerText)"), existingEntry.lastChild)
            ShortenReferences.DEFAULT.process(existingEntry)
        } else {
            owner.addAnnotation(annotationFqName, annotationInnerText, useSiteTarget = useSiteTarget)
        }
    }
}

class HighPriorityReplaceAnnotationFix(
    annotationEntry: KtAnnotationEntry,
    modifierListOwner: SmartPsiElementPointer<KtModifierListOwner>,
    annotationFqName: FqName,
    argumentFqName: FqName? = null,
    useSiteTarget: AnnotationUseSiteTarget? = null,
    existingReplacementAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
) : ReplaceAnnotationFix(
    annotationEntry,
    modifierListOwner,
    annotationFqName,
    argumentFqName,
    useSiteTarget,
    existingReplacementAnnotationEntry
), HighPriorityAction
