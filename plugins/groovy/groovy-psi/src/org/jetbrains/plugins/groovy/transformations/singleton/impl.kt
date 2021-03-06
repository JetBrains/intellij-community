// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.util.text.nullize
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

internal const val singletonFqn = GroovyCommonClassNames.GROOVY_LANG_SINGLETON
@NonNls internal const val singletonOriginInfo = "by @Singleton"

@NlsSafe
fun PsiAnnotation.getPropertyName(): String = findDeclaredDetachedValue("property").stringValue().nullize(true) ?: "instance"

internal fun getAnnotation(identifier: PsiElement?): GrAnnotation? {
  val parent = identifier?.parent as? GrMethod ?: return null
  if (!parent.isConstructor) return null
  val clazz = parent.containingClass as? GrTypeDefinition ?: return null
  return AnnotationUtil.findAnnotation(clazz, singletonFqn) as? GrAnnotation
}
