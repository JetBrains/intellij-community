// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition


/**
 * Check features introduced in groovy 3.0
 */
class GroovyAnnotator30(private val myHolder: AnnotationHolder, private val atLeast30: Boolean) : GroovyElementVisitor() {

  override fun visitModifierList(modifierList: GrModifierList) {
    checkDefaultModifier(modifierList)
  }

  private fun checkDefaultModifier(modifierList: GrModifierList) {
    val modifier = modifierList.getModifier(PsiModifier.DEFAULT) ?: return
    if (!atLeast30) {
      myHolder.createErrorAnnotation(modifier, GroovyBundle.message("default.modifier.in.old.versions"))
      return
    }

    val parentClass = PsiTreeUtil.getParentOfType(modifier, PsiClass::class.java) ?: return
    if (!parentClass.isInterface || (parentClass as? GrTypeDefinition)?.isTrait == true) {
      val annotation = myHolder.createWarningAnnotation(modifier, GroovyBundle.message("illegal.default.modifier"))
      registerFix(annotation, GrRemoveModifierFix(PsiModifier.DEFAULT, GroovyBundle.message("illegal.default.modifier.fix")), modifier)
    }
  }
}

