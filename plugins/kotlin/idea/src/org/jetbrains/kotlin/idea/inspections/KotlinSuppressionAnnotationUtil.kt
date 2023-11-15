// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.SuppressionAnnotationUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UExpression

internal class KotlinSuppressionAnnotationUtil : SuppressionAnnotationUtil {

    private val suppressionAnnotationClassQualifiedName = Suppress::class.java.name

    override fun isSuppressionAnnotation(annotation: UAnnotation): Boolean {
        return annotation.sourcePsi?.text?.contains("Suppress") == true && // avoid resolving
                annotation.qualifiedName == suppressionAnnotationClassQualifiedName
    }

    override fun getSuppressionAnnotationAttributeExpressions(annotation: UAnnotation): List<UExpression> {
        return annotation.attributeValues
            .filter { it.name == null || it.name == "names" }
            .map { it.expression }
    }

    override fun getRemoveAnnotationQuickFix(annotation: PsiElement): LocalQuickFix? {
        if (annotation is KtAnnotationEntry) {
            val fix = RemoveAnnotationFix(CommonQuickFixBundle.message("fix.remove.annotation.text", "Suppress"), annotation)
            return IntentionWrapper.wrapToQuickFixes(arrayOf(fix), annotation.containingFile).takeIf { it.size == 1 }?.first()
        }
        return null
    }
}
