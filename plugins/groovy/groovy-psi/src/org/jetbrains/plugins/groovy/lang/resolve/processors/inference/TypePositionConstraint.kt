// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter

class TypePositionConstraint(
  private val expectedType: ExpectedType,
  private val rightType: PsiType?,
  private val context: PsiElement
) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<in ConstraintFormula>): Boolean {
    if (rightType != null) {
      for (extension in GrTypeConverter.EP_NAME.extensionList) {
        if (!extension.isApplicableTo(expectedType.position)) {
          continue
        }
        val reduced = extension.reduceTypeConstraint(expectedType.type, rightType, expectedType.position, context)
        if (reduced != null) {
          constraints += reduced
          return true
        }
      }
    }
    constraints += TypeConstraint(expectedType.type, rightType, context)
    return true
  }
}
