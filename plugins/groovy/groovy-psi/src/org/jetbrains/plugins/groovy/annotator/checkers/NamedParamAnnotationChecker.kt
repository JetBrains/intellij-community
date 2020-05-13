// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.GROOVY_TRANSFORM_NAMED_PARAM

class NamedParamAnnotationChecker : CustomAnnotationChecker() {

  override fun checkArgumentList(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (GROOVY_TRANSFORM_NAMED_PARAM != annotation.qualifiedName) return false
    val annotationClass = ResolveUtil.resolveAnnotation(annotation) ?: return false
    val r = checkAnnotationArguments(annotationClass, annotation.parameterList.attributes, false)
    if (r?.getFirst() != null) {
      holder.newAnnotation(HighlightSeverity.ERROR, r.second).range(r.first).create()
    }
    return true
  }
}
