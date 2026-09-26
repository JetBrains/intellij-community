// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTraitTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

class GroovyAnnotator18(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitTypeDefinition(typeDefinition: GrTypeDefinition) {
    val modifierList = typeDefinition.modifierList ?: return
    if (typeDefinition.containingClass == null && typeDefinition !is GrTraitTypeDefinition) {
      checkModifierIsNotAllowed(modifierList, PsiModifier.STATIC, holder)
    }
  }
}
