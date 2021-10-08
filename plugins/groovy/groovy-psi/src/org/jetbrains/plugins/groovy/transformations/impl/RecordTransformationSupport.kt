// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl
import org.jetbrains.plugins.groovy.lang.psi.util.isRecordTransformationApplied
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

class RecordTransformationSupport : AstTransformationSupport {
  override fun applyTransformation(context: TransformationContext) = with(context) {
    if (!isRecordTransformationApplied(codeClass)) {
      return
    }
    performClassTransformation()
    val currentFields = fields.toList()
    for (field in currentFields) {
      generateRecordProperty(field)
    }
  }

  private fun TransformationContext.performClassTransformation() {
    val modifierList = codeClass.modifierList
    if (modifierList != null) {
      addModifier(modifierList, GrModifier.FINAL)
      if (this.codeClass.containingClass != null) {
        addModifier(modifierList, GrModifier.STATIC)
      }
    }
  }

  private fun TransformationContext.generateRecordProperty(field: GrField) {
    addMethod(GrAccessorMethodImpl(field, false, field.name, GrModifierFlags.FINAL_MASK or getVisibilityMask(field)))
  }

  private fun getVisibilityMask(owner: PsiModifierListOwner): Int {
    return if (owner.modifierList?.hasExplicitModifier(GrModifier.PRIVATE) == true) GrModifierFlags.PRIVATE_MASK
    else if (owner.modifierList?.hasExplicitModifier(GrModifier.PROTECTED) == true) GrModifierFlags.PROTECTED_MASK
    else GrModifierFlags.PUBLIC_MASK
  }
}