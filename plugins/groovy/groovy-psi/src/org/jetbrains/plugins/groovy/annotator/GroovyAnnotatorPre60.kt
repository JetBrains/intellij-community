// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class GroovyAnnotatorPre60(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitModifierList(modifierList: GrModifierList) {
    val modifier = modifierList.getModifier(GrModifier.VAL)
    if (modifier != null) {
      val parent = modifierList.parent
      if (parent !is GrMethod && (parent !is GrVariableDeclaration || !parent.isTuple)) {
        val message = message("unsupported.val.declaration")
        val builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifier)
        registerLocalFix(builder, GrRemoveModifierFix(GrModifier.VAL), modifier,
                         message, ProblemHighlightType.ERROR, modifier.textRange)
        builder.create()
      }
    }
  }
}