// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.transformations.immutable.hasImmutableAnnotation
import org.jetbrains.plugins.groovy.transformations.immutable.isImmutable
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.collectAllParamsFromNamedVariantMethod

/**
 * Check features introduced in groovy 2.5
 */
class GroovyAnnotator25(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitMethod(method: GrMethod) {
    collectAllParamsFromNamedVariantMethod(method).groupBy { it.first }.filter { it.value.size > 1 }.forEach { (name, parameters) ->
      val parametersList = parameters.joinToString { "'${it.second.name}'" }
      parameters.drop(1).map { (_, parameter) -> parameter }.forEach {
        holder.createErrorAnnotation(it.nameIdentifierGroovy, GroovyBundle.message("duplicating.named.parameter", name, parametersList))
      }
    }
    super.visitMethod(method)
  }

  override fun visitField(field: GrField) {
    immutableCheck(field)
    super.visitField(field)
  }

  private fun immutableCheck(field: GrField) {
    val containingClass = field.containingClass ?: return
    if (field.isProperty && hasImmutableAnnotation(containingClass) && !isImmutable(field)) {
      holder.createErrorAnnotation(field.nameIdentifierGroovy, GroovyBundle.message("field.should.be.immutable", field.name))
    }
  }
}