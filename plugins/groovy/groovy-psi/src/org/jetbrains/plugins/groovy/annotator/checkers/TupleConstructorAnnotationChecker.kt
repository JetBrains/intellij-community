// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

class TupleConstructorAnnotationChecker : CustomAnnotationChecker() {

  companion object {
    fun registerIdentifierListError(holder: AnnotationHolder, element: PsiElement) =
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("explicit.includes.and.excludes"))
        .range(element)
        .create()

    fun registerClosureError(holder: AnnotationHolder, element: PsiElement) =
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("require.closure.as.attribute.value"))
        .range(element)
        .create()
  }

  override fun checkArgumentList(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (annotation.qualifiedName != GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR) {
      return false
    }
    val excludes = AnnotationUtil.findDeclaredAttribute(annotation, "excludes")
    val includes = AnnotationUtil.findDeclaredAttribute(annotation, "includes")
    if (includes != null && excludes != null) {
      registerIdentifierListError(holder, excludes)
      registerIdentifierListError(holder, includes)
    }
    val pre = AnnotationUtil.findDeclaredAttribute(annotation, "pre")?.value
    if (pre != null && pre !is GrFunctionalExpression) {
      registerClosureError(holder, pre)
    }
    val post = AnnotationUtil.findDeclaredAttribute(annotation, "post")?.value
    if (post != null && post !is GrFunctionalExpression) {
      registerClosureError(holder, post)
    }
    return false
  }
}