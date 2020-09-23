// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

class AutoFinalTransformationSupport : AstTransformationSupport {
  override fun applyTransformation(context: TransformationContext) {
    context.codeClass.acceptChildren(object : GroovyRecursiveElementVisitor() {
      override fun visitModifierList(modifierList: GrModifierList) {
        context.addModifier(modifierList, PsiModifier.FINAL)
      }
    })
  }
}