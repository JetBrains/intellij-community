// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

class NamedParamAnnotationChecker : CustomAnnotationChecker() {
  override fun checkArgumentList(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (GroovyCommonClassNames.GROOVY_TRANSFORM_NAMED_PARAM != annotation.qualifiedName) return false
    val annotationClass = ResolveUtil.resolveAnnotation(annotation) ?: return false
    CustomAnnotationChecker.checkAnnotationArguments(holder, annotationClass, annotation.classReference,
                                                     annotation.parameterList.attributes, false)

    return true
  }
}