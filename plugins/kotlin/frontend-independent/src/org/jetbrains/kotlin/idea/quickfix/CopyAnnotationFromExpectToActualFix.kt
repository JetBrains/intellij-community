// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner

internal class CopyAnnotationFromExpectToActualFix(
    actualElement: KtModifierListOwner,
    @SafeFieldForPreview private val expectAnnotationEntry: KtAnnotationEntry,
    @SafeFieldForPreview private val annotationClassId: ClassId,
) : KotlinQuickFixAction<KtModifierListOwner>(actualElement) {

    private val expectAnnotationShortName: String = expectAnnotationEntry.shortName?.toString() ?: "<unknown>"

    override fun getText(): String {
        return KotlinBundle.message("fix.copy.mismatched.annotation.to.actual.declaration.may.change.semantics", expectAnnotationShortName)
    }

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val actualElement = element ?: return
        val innerText = expectAnnotationEntry.valueArguments.joinToString { it.asElement().text }
        actualElement.addAnnotation(annotationClassId, innerText.takeIf { it.isNotEmpty() }, searchForExistingEntry = false)
    }
}

