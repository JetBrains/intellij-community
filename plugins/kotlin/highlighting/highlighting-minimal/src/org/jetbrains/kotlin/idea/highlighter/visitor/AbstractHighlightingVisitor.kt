// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter.visitor

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.highlighter.HighlightingFactory
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid

@ApiStatus.Internal
abstract class AbstractHighlightingVisitor(protected val holder: HighlightInfoHolder): KtVisitorVoid() {
    protected fun highlightName(element: PsiElement, highlightInfoType: HighlightInfoType, message: @DetailedDescription String? = null) {
        holder.add(HighlightingFactory.highlightName(element, highlightInfoType, message)?.create())
    }

    protected fun highlightName(project: Project, element: PsiElement, textRange: TextRange, highlightInfoType: HighlightInfoType, message: @DetailedDescription String? = null) {
        holder.add(HighlightingFactory.highlightName(project, element, textRange, highlightInfoType, message).create())
    }

    protected fun highlightNamedDeclaration(declaration: KtNamedDeclaration, attributesKey: HighlightInfoType) {
        declaration.nameIdentifier?.let { highlightName(it, attributesKey) }
    }
}