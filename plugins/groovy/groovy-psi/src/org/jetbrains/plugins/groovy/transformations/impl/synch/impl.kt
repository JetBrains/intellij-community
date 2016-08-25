/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.transformations.impl.synch

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyElementPattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns

val ANNO_FQN = "groovy.transform.Synchronized"
val LOCK_NAME = "\$lock"
val STATIC_LOCK_NAME = "\$LOCK"

internal val PATTERN: GroovyElementPattern.Capture<out GrLiteral> = GroovyPatterns.stringLiteral().annotationParam(
    StandardPatterns.string().matches(ANNO_FQN), "value"
)

internal val GrLiteral.annotatedMethod: GrMethod?
  get() = parent?.parent?.parent?.parent?.parent as? GrMethod

internal val GrLiteral.containingClass: GrTypeDefinition?
  get() = annotatedMethod?.containingClass as? GrTypeDefinition

fun <T> Sequence<T>.isNotEmpty() = none().not()

fun getMethodsImplicitlyReferencingLock(field: GrField) = getAllMethodsReferencingLock(field).filter {
  it.second.findDeclaredAttributeValue(null) == null
}

internal fun getAllMethodsReferencingLock(field: GrField) = field.containingClass?.let {
  getAllMethodsReferencingLock(it, field.name)
} ?: emptySequence()

private fun getAllMethodsReferencingLock(clazz: PsiClass, lockName: String) = clazz.methods.asSequence().mapNotNull { method ->
  val annotation = AnnotationUtil.findAnnotation(method, ANNO_FQN)
  if (annotation != null) {
    val referencesLock = annotation.referencesLock(lockName) {
      if (method.isStatic()) STATIC_LOCK_NAME else LOCK_NAME
    }
    if (referencesLock) return@mapNotNull method to annotation
  }
  null
}

private inline fun PsiAnnotation.referencesLock(lockName: String, defaultLockName: () -> String): Boolean {
  val value = AnnotationUtil.getDeclaredStringAttributeValue(this, DEFAULT_REFERENCED_METHOD_NAME) ?: defaultLockName()
  return value == lockName
}

internal fun PsiModifierListOwner.isStatic() = hasModifierProperty(PsiModifier.STATIC)