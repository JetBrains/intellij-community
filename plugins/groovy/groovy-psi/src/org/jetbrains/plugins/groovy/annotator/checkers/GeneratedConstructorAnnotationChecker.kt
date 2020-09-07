// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil.inferClosureAttribute
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes.CALL_SUPER
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes.EXCLUDES
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes.INCLUDES
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes.POST
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes.PRE
import org.jetbrains.plugins.groovy.lang.resolve.ast.constructorGeneratingAnnotations
import org.jetbrains.plugins.groovy.lang.resolve.ast.contributor.SyntheticKeywordConstructorContributor.Companion.isSyntheticConstructorCall
import org.jetbrains.plugins.groovy.lang.resolve.ast.getIdentifierList

class GeneratedConstructorAnnotationChecker : CustomAnnotationChecker() {

  companion object {
    @JvmStatic
    fun isSuperCalledInPre(annotation: PsiAnnotation): Boolean =
      (inferClosureAttribute(annotation, PRE)?.statements?.firstOrNull() as? GrMethodCall)
        .run(::isSyntheticConstructorCall)

    private fun registerIdentifierListError(holder: AnnotationHolder, element: PsiElement) =
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("explicit.includes.and.excludes"))
        .range(element)
        .create()

    private fun registerClosureError(holder: AnnotationHolder, element: PsiElement) =
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("require.closure.as.attribute.value"))
        .range(element)
        .create()

    private fun registerDuplicateSuperError(holder: AnnotationHolder, element: PsiElement) =
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("super.is.not.allowed.in.pre.with.call.super"))
        .range(element)
        .create()
  }

  override fun checkArgumentList(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (annotation.qualifiedName !in constructorGeneratingAnnotations) {
      return false
    }
    val excludes = getIdentifierList(annotation, EXCLUDES)
    val includes = AnnotationUtil.findDeclaredAttribute(annotation, INCLUDES)
    if (includes != null && excludes != null && excludes.isNotEmpty()) {
      registerIdentifierListError(holder, AnnotationUtil.findDeclaredAttribute(annotation, EXCLUDES)!!)
      registerIdentifierListError(holder, includes)
    }
    val pre = AnnotationUtil.findDeclaredAttribute(annotation, PRE)?.value
    if (pre != null && pre !is GrFunctionalExpression) {
      registerClosureError(holder, pre)
    }
    val post = AnnotationUtil.findDeclaredAttribute(annotation, POST)?.value
    if (post != null && post !is GrFunctionalExpression) {
      registerClosureError(holder, post)
    }
    val callSuper = AnnotationUtil.findDeclaredAttribute(annotation, CALL_SUPER)
    if (callSuper != null && pre != null &&
        GrAnnotationUtil.inferBooleanAttribute(annotation, CALL_SUPER) == true &&
        isSuperCalledInPre(annotation)) {
      registerDuplicateSuperError(holder, callSuper)
    }
    return false
  }
}