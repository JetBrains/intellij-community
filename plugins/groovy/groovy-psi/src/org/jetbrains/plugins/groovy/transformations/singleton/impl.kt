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
package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.util.text.nullize
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

internal val singletonFqn = GroovyCommonClassNames.GROOVY_LANG_SINGLETON
internal val singletonOriginInfo = "by @Singleton"

fun PsiAnnotation.getPropertyName(): String = findDeclaredDetachedValue("property").stringValue().nullize(true) ?: "instance"

internal fun getAnnotation(identifier: PsiElement?): GrAnnotation? {
  val parent = identifier?.parent as? GrMethod ?: return null
  if (!parent.isConstructor) return null
  val clazz = parent.containingClass as? GrTypeDefinition ?: return null
  return AnnotationUtil.findAnnotation(clazz, singletonFqn) as? GrAnnotation
}
