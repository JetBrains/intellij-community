// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.highlighting.HighlightSink

internal class UnresolvedReferenceAnnotatorSink(private val annotationHolder: AnnotationHolder) : HighlightSink {

  override fun registerProblem(highlightElement: PsiElement,
                               highlightType: ProblemHighlightType,
                               message: String,
                               actions: List<IntentionAction>) {
    val annotation = annotationHolder.createErrorAnnotation(highlightElement, message)
    annotation.highlightType = highlightType
    actions.forEach(annotation::registerFix)
  }
}
