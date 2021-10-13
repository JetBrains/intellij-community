// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrRecordUtils")

package org.jetbrains.plugins.groovy.lang.psi.util

import org.jetbrains.plugins.groovy.transformations.TransformationContext

/**
 * According to Groovy compiler, no record transformation is done when there is no property handler.
 */
fun isRecordTransformationApplied(context: TransformationContext): Boolean {
  return context.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_RECORD_BASE) != null &&
         context.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_PROPERTY_OPTIONS) != null
}