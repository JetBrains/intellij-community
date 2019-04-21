// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * @author knisht
 */

class InferenceVariableNode(val inferenceVariable: InferenceVariable) {
  val supertypes: MutableSet<InferenceVariableNode> = LinkedHashSet()
  val subtypes: MutableSet<InferenceVariableNode> = LinkedHashSet()
  val weakSupertypes: MutableSet<InferenceVariableNode> = LinkedHashSet()
  val weakSubtypes: MutableSet<InferenceVariableNode> = LinkedHashSet()
  var directParent: InferenceVariableNode? = null

  /**
   * Sets up node dependencies basing on inference variable bounds.
   */
  fun collectDependencies(variable: InferenceVariable,
                          session: GroovyInferenceSession,
                          nodes: Map<InferenceVariable, InferenceVariableNode>) {
    val weakUpperBoundHandler: (InferenceVariableNode) -> Unit = {
      if (it != this) {
        weakSupertypes.add(it);
        it.weakSubtypes.add(this)
      }
    }
    val weakLowerBoundHandler: (InferenceVariableNode) -> Unit = {
      if (it != this) {
        weakSubtypes.add(it);
        it.weakSupertypes.add(this)
      }
    }
    val strongUpperBoundHandler: (InferenceVariableNode) -> Unit = {
      supertypes.add(it)
      it.subtypes.add(this)
    }
    val strongLowerBoundHandler: (InferenceVariableNode) -> Unit = {
      subtypes.add(it)
      it.supertypes.add(this)
    }
    collectBounds(weakUpperBoundHandler, strongUpperBoundHandler, InferenceBound.UPPER, variable, session, nodes)
    collectBounds(weakLowerBoundHandler, strongLowerBoundHandler, InferenceBound.LOWER, variable, session, nodes)
  }


  private fun collectBounds(weakRelationHandler: (InferenceVariableNode) -> Unit,
                            strongRelationHandler: (InferenceVariableNode) -> Unit,
                            relationBound: InferenceBound,
                            variable: InferenceVariable,
                            session: GroovyInferenceSession,
                            nodes: Map<InferenceVariable, InferenceVariableNode>) {
    val typeVisitor = object : PsiTypeVisitor<PsiType>() {

      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val dependentVariable = session.getInferenceVariable(classType)
        if (dependentVariable != null && dependentVariable in nodes) {
          weakRelationHandler(nodes.getValue(dependentVariable))
        }
        else {
          classType.parameters.forEach { it.accept(this) }
        }
        return super.visitClassType(classType)
      }

      override fun visitIntersectionType(intersectionType: PsiIntersectionType?): PsiType? {
        intersectionType ?: return intersectionType
        intersectionType.conjuncts.forEach { it.accept(this) }
        return super.visitIntersectionType(intersectionType)
      }
    }

    for (dependentType in variable.getBounds(relationBound)
      .flatMap { if (it is PsiIntersectionType) it.conjuncts.asIterable() else listOf(it) }) {
      val dependentVariable = session.getInferenceVariable(dependentType)
      if (dependentVariable != null && dependentVariable in nodes.keys && dependentVariable != inferenceVariable) {
        strongRelationHandler(nodes.getValue(dependentVariable))
      }
      else {
        dependentType.accept(typeVisitor)
      }
    }

  }

  override fun toString(): String {
    return "{" + inferenceVariable.type().toString() + "}"
  }
}