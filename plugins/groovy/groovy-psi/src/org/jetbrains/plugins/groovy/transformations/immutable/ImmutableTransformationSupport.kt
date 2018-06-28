// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.immutable

import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import org.jetbrains.plugins.groovy.lang.psi.impl.booleanValue
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_IMMUTABLE
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext
import org.jetbrains.plugins.groovy.transformations.plusAssign

class ImmutableTransformationSupport : AstTransformationSupport {

  override fun applyTransformation(context: TransformationContext) {
    val annotation = context.getAnnotation(GROOVY_TRANSFORM_IMMUTABLE) ?: context.getAnnotation(GROOVY_TRANSFORM_IMMUTABLE_BASE) ?: return
    if (annotation.findDeclaredDetachedValue(immutableCopyWith)?.booleanValue() != true) return
    if (context.fields.isEmpty()) return
    if (context.findMethodsByName(immutableCopyWith, false).any { it.parameters.size == 1 }) return

    context += context.memberBuilder.method(immutableCopyWith) {
      addParameter("args", JAVA_UTIL_MAP)
      returnType = createType(context.codeClass)
      navigationElement = annotation
      methodKind = immutableCopyWithKind
      originInfo = immutableOrigin
    }
  }
}
