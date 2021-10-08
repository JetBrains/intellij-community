// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrRecordUtils")

package org.jetbrains.plugins.groovy.lang.psi.util

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

/**
 * According to Groovy compiler, no record transformation is done when there is no property handler.
 */
fun isRecordTransformationApplied(typedef : GrTypeDefinition) : Boolean {
  return typedef.hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_RECORD_BASE) &&
         typedef.hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_PROPERTY_OPTIONS)
}