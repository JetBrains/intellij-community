// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.annotators

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

class EditorConfigAnnotator : Annotator, DumbAware {
  override fun annotate(element: PsiElement, holder: AnnotationHolder): Unit =
    element.accept(EditorConfigAnnotatorVisitor(holder))
}
