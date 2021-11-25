// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.addUseSiteTarget
import org.jetbrains.kotlin.idea.intentions.applicableUseSiteTargets
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile

class AddAnnotationUseSiteTargetFix(
    annotationEntry: KtAnnotationEntry,
    @SafeFieldForPreview
    private val useSiteTargets: List<AnnotationUseSiteTarget>
) : KotlinQuickFixAction<KtAnnotationEntry>(annotationEntry) {

    override fun getText(): String {
        return if (useSiteTargets.size == 1) {
            KotlinBundle.message("text.add.use.site.target.0", useSiteTargets.first().renderName)
        } else {
            KotlinBundle.message("add.use.site.target")
        }
    }

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.addUseSiteTarget(useSiteTargets, editor)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtAnnotationEntry>? {
            val entry = diagnostic.psiElement as? KtAnnotationEntry ?: return null
            val applicableUseSiteTargets = entry.applicableUseSiteTargets()
            if (applicableUseSiteTargets.isEmpty()) return null
            return AddAnnotationUseSiteTargetFix(entry, applicableUseSiteTargets)
        }
    }

}