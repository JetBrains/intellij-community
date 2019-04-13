// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession

/**
 * @author knisht
 */

class InferenceVariableNode(val inferenceVariable: InferenceVariable) {
  val supertypes: MutableSet<InferenceVariableNode> = HashSet()
  val subtypes: MutableSet<InferenceVariableNode> = HashSet()
  val weakDependencies: MutableSet<InferenceVariableNode> = HashSet()
  var directParent: InferenceVariableNode? = null


  fun collectDependencies(variable: InferenceVariable,
                          session: GroovyInferenceSession,
                          nodes: Map<InferenceVariable, InferenceVariableNode>) {
    val typeVisitor = object : PsiTypeVisitor<PsiType>() {

      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val dependentVariable = session.getInferenceVariable(classType)
        if (dependentVariable != null && dependentVariable in nodes) {
          weakDependencies.add(nodes.getValue(dependentVariable))
        }
        else {
          classType.parameters.forEach { it.accept(this) }
        }
        return super.visitClassType(classType)
      }

    }

    for (upperType in variable.getBounds(InferenceBound.UPPER)) {
      val dependentVariable = session.getInferenceVariable(upperType)
      if (dependentVariable != null && dependentVariable in nodes.keys) {
        supertypes.add(nodes.getValue(dependentVariable))
        nodes.getValue(dependentVariable).subtypes.add(this)
      }
      else {
        upperType.accept(typeVisitor)
      }
    }
  }
}