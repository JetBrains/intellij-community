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
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns

val ANNO_FQN: String = "groovy.transform.Synchronized"
val LOCK_NAME: String = "\$lock"
val STATIC_LOCK_NAME: String = "\$LOCK"

internal val PATTERN: ElementPattern<out GrLiteral> = GroovyPatterns.stringLiteral().annotationParam(
    StandardPatterns.string().equalTo(ANNO_FQN), DEFAULT_REFERENCED_METHOD_NAME
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