// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.patterns

import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair

open class GroovyAnnotationArgumentPattern<T : GrAnnotationNameValuePair, P : GroovyAnnotationArgumentPattern<T, P>>(
  clazz: Class<T>
) : GroovyElementPattern<T, P>(clazz) {

  fun withArgumentName(name: String?): GroovyAnnotationArgumentPattern<T, P> {
    return with(object : PatternCondition<T>("with name: $name") {
      override fun accepts(t: T, context: ProcessingContext?): Boolean = t.name == name
    })
  }

  class Capture : GroovyAnnotationArgumentPattern<GrAnnotationNameValuePair, Capture>(GrAnnotationNameValuePair::class.java)
}