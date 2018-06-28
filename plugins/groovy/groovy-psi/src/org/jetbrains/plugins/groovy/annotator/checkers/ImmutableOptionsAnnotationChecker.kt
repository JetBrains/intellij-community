// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.transformations.immutable.GROOVY_TRANSFORM_IMMUTABLE_OPTIONS
import org.jetbrains.plugins.groovy.transformations.immutable.KNOWN_IMMUTABLES_OPTION
import org.jetbrains.plugins.groovy.transformations.impl.synch.isStatic

class ImmutableOptionsAnnotationChecker : CustomAnnotationChecker() {
  override fun checkArgumentList(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (GROOVY_TRANSFORM_IMMUTABLE_OPTIONS != annotation.qualifiedName) return false
    val containingClass = PsiTreeUtil.getParentOfType(annotation, PsiClass::class.java) ?: return false
    val immutableFieldsList = AnnotationUtil.findDeclaredAttribute(annotation, KNOWN_IMMUTABLES_OPTION)?.value ?: return false

    val fields = (immutableFieldsList as? GrAnnotationArrayInitializer)?.initializers ?: return false
    if (fields.isEmpty()) return false
    val names = containingClass.fields.filter { !it.isStatic() }.map { it.name }
    fields.filterIsInstance<GrLiteral>().forEach {
      val value = it.value
      if (value is String && !names.contains(value)) {
        holder.createErrorAnnotation(it, GroovyBundle.message("immutable.options.field.not.exist", value))
      }
    }
    return false
  }
}