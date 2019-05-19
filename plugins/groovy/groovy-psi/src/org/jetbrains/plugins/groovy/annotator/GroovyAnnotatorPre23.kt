// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTraitTypeDefinition

class GroovyAnnotatorPre23(private val holder: AnnotationHolder, private val version: String) : GroovyElementVisitor() {

  override fun visitTraitDefinition(traitTypeDefinition: GrTraitTypeDefinition) {
    val keyword = traitTypeDefinition.node.findChildByType(GroovyElementTypes.KW_TRAIT) ?: error("trait without keyword")
    holder.createErrorAnnotation(keyword, message("unsupported.traits.0", version))
  }
}
