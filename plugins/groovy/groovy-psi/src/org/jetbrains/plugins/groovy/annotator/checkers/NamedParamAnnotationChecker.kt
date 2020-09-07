// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.GROOVY_TRANSFORM_NAMED_PARAM

class NamedParamAnnotationChecker : CustomAnnotationChecker() {

  override fun checkArgumentList(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (GROOVY_TRANSFORM_NAMED_PARAM != annotation.qualifiedName) return false
    val annotationClass = ResolveUtil.resolveAnnotation(annotation) ?: return false
    val r: Pair<PsiElement, String>? = checkAnnotationArguments(annotationClass, annotation.parameterList.attributes, false)
    if (r?.getFirst() != null) {
      val message = r.second // NON-NLS
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(r.first).create()
    }
    return true
  }
}
