// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.quickfix.MissingConstructorBracketsFix
import org.jetbrains.kotlin.psi.KtClass

class MissingConstructorBracketsAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KtClass) return
        val primaryConstructor = element.primaryConstructor ?: return
        if (primaryConstructor.valueParameterList != null) return
        val startRange = primaryConstructor.getConstructorKeyword()?.textRange?.endOffset ?: return
        holder.newSilentAnnotation(HighlightSeverity.ERROR)
            .range(TextRange.create(startRange, startRange + 1))
            .withFix(MissingConstructorBracketsFix(primaryConstructor))
            .create()
    }
}