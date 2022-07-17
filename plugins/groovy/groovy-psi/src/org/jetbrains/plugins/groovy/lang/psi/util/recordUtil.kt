// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrRecordUtils")

package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.transformations.TransformationContext

/**
 * According to Groovy compiler, no record transformation is done when there is no property handler.
 */
fun isRecordTransformationApplied(context: TransformationContext): Boolean {
  return context.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_RECORD_BASE) != null &&
         context.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_PROPERTY_OPTIONS) != null
}

fun getCompactConstructor(typedef : GrTypeDefinition) : GrMethod? =
  typedef.codeConstructors.find(GrMethod::isCompactConstructor)

fun GrMethod.isCompactConstructor(): Boolean =
  isConstructor && parameterList.text == ""

internal fun forbidRecord(holder: AnnotationHolder, recordDefinition: GrRecordDefinition) {
  holder
    .newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("inspection.message.records.are.available.in.groovy.4.or.later"))
    .range(recordDefinition.firstChild.siblings().first { it.elementType === org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kRECORD })
    .create()
}
