// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl.synch

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.patterns.ElementPattern
import com.intellij.psi.*
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns

const val ANNO_FQN: String = "groovy.transform.Synchronized"
const val LOCK_NAME: String = "\$lock"
const val STATIC_LOCK_NAME: String = "\$LOCK"

internal val PATTERN: ElementPattern<out GrLiteral> = GroovyPatterns.stringLiteral().annotationParam(
    ANNO_FQN, DEFAULT_REFERENCED_METHOD_NAME
)

fun getImplicitLockUsages(field: GrField): Sequence<PsiAnnotation> {
  val static = when (field.name) {
    LOCK_NAME -> false
    STATIC_LOCK_NAME -> true
    else -> return emptySequence()
  }
  val clazz = field.containingClass ?: return emptySequence()
  return getImplicitLockUsages(clazz, static)
}

private fun getImplicitLockUsages(clazz: PsiClass, static: Boolean) = clazz.methods.asSequence().filter { method ->
  static == method.isStatic()
}.mapNotNull { method ->
  AnnotationUtil.findAnnotation(method, ANNO_FQN)
}.filter { anno ->
  anno.findDeclaredAttributeValue(null) == null
}

internal fun getMethodsReferencingLock(field: GrField): Sequence<PsiMethod> = field.containingClass?.let {
  getMethodsReferencingLock(it, field.name)
} ?: emptySequence()

private fun getMethodsReferencingLock(clazz: PsiClass, requiredLockName: String) = clazz.methods.asSequence().filter(
    fun(method: PsiMethod): Boolean {
      val annotation = AnnotationUtil.findAnnotation(method, ANNO_FQN) ?: return false
      val referencedLock = AnnotationUtil.getDeclaredStringAttributeValue(
          annotation, null
      ) ?: if (method.isStatic()) STATIC_LOCK_NAME else LOCK_NAME
      return requiredLockName == referencedLock
    }
)

internal fun PsiModifierListOwner.isStatic() = hasModifierProperty(PsiModifier.STATIC)