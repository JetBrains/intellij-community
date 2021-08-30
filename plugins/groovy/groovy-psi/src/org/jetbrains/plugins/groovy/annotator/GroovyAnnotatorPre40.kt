// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList

class GroovyAnnotatorPre40(private val holder: AnnotationHolder) : GroovyElementVisitor() {
  fun registerModifierProblem(modifier : PsiElement, inspectionMessage : @Nls String, fixMessage : @Nls String) {
    val builder = holder.newAnnotation(HighlightSeverity.ERROR,
                                       inspectionMessage).range(modifier)
    registerLocalFix(builder, GrRemoveModifierFix(modifier.text, fixMessage), modifier, inspectionMessage, ProblemHighlightType.ERROR, modifier.textRange)
    builder.create()
  }

  override fun visitModifierList(modifierList: GrModifierList) {
    val sealed = modifierList.getModifier(GrModifier.SEALED)
    if (sealed != null) {
      registerModifierProblem(sealed, GroovyBundle.message("inspection.message.modifier.sealed.available.with.groovy.or.later"), GroovyBundle.message("illegal.sealed.modifier.fix"))
    }
    val nonSealed = modifierList.getModifier(GrModifier.NON_SEALED)
    if (nonSealed != null) {
      registerModifierProblem(nonSealed, GroovyBundle.message("inspection.message.modifier.nonsealed.available.with.groovy.or.later"), GroovyBundle.message("illegal.nonsealed.modifier.fix"))
    }
  }
}
