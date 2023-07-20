// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.highlighting.HighlightingFactory
import org.jetbrains.kotlin.idea.base.highlighting.isNameHighlightingEnabled
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid

@ApiStatus.Internal
abstract class AbstractHighlightingVisitor(protected val holder: HighlightInfoHolder): KtVisitorVoid() {
    protected fun createInfoAnnotation(textRange: TextRange, message: String? = null, textAttributes: TextAttributesKey? = null) {
        HighlightingFactory.addInfoAnnotation(holder, textRange, message, textAttributes)
    }

    protected fun createInfoAnnotation(element: PsiElement, message: String? = null, textAttributes: TextAttributesKey) {
        createInfoAnnotation(element.textRange, message, textAttributes)
    }

    protected fun createInfoAnnotation(element: PsiElement, message: String? = null) {
        createInfoAnnotation(element.textRange, message)
    }

    protected fun highlightName(element: PsiElement, attributesKey: TextAttributesKey, message: String? = null) {
        if (element.project.isNameHighlightingEnabled && !element.textRange.isEmpty) {
            createInfoAnnotation(element, message, attributesKey)
        }
    }

    protected fun highlightName(project: Project, textRange: TextRange, attributesKey: TextAttributesKey, message: String? = null) {
        if (project.isNameHighlightingEnabled) {
            createInfoAnnotation(textRange, message, attributesKey)
        }
    }

    protected fun highlightNamedDeclaration(declaration: KtNamedDeclaration, attributesKey: TextAttributesKey) {
        declaration.nameIdentifier?.let { highlightName(it, attributesKey) }
    }
}