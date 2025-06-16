// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner

internal class CopyAnnotationFromExpectToActualFix(
    actualElement: KtModifierListOwner,
    private val expectAnnotationEntry: KtAnnotationEntry,
    private val annotationClassId: ClassId,
) : PsiUpdateModCommandAction<KtModifierListOwner>(actualElement) {

    private val expectAnnotationShortName: String = expectAnnotationEntry.shortName?.toString() ?: "<unknown>"

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.copy.mismatched.annotation.to.actual.declaration.may.change.semantics", expectAnnotationShortName)

    override fun invoke(
        context: ActionContext,
        element: KtModifierListOwner,
        updater: ModPsiUpdater,
    ) {
        val innerText = expectAnnotationEntry.valueArguments.joinToString { it.asElement().text }
        element.addAnnotation(annotationClassId, innerText.takeIf { it.isNotEmpty() }, searchForExistingEntry = false)
    }
}

