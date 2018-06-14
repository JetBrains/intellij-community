// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.collectAllParamsFromNamedVariantMethod

/**
 * Check features introduced in groovy 2.5
 */
class GroovyAnnotator25(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitMethod(method: GrMethod) {
    collectAllParamsFromNamedVariantMethod(method).groupBy { it.first }.filter { it.value.size > 1 }.forEach { entry ->
      val clashingParameters: List<Pair<String, GrParameter>> = entry.value
      val parametersList = clashingParameters.joinToString(", ", "", "") { "'${it.second.name}'" }
      clashingParameters.drop(1).map { it.second }.forEach {
        holder.createErrorAnnotation(it, GroovyBundle.message("duplicating.named.parameter", entry.key, parametersList))
      }
    }
    super.visitMethod(method)
  }
}