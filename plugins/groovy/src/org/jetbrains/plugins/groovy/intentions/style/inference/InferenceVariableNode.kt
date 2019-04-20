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
  var graphDepth: Int = 0

  fun collectDependencies(variable: InferenceVariable,
                          session: GroovyInferenceSession,
                          nodes: Map<InferenceVariable, InferenceVariableNode>) {
    for (upperType in variable.getBounds(InferenceBound.UPPER)
      .flatMap { if (it is PsiIntersectionType) it.conjuncts.asIterable() else arrayOf(it).asIterable() }) {
      val dependentVariable = session.getInferenceVariable(upperType)
      if (dependentVariable != null && dependentVariable in nodes.keys && dependentVariable != inferenceVariable) {
        supertypes.add(nodes.getValue(dependentVariable))
        nodes.getValue(dependentVariable).subtypes.add(this)
      }
      else {
        collectBounds({
                        if (it != this) {
                          weakSupertypes.add(it);
                          it.weakSubtypes.add(this)
                        }
                      }, upperType, session, nodes)
      }
    }
    for (lowerType in variable.getBounds(InferenceBound.LOWER)
      .flatMap { if (it is PsiIntersectionType) it.conjuncts.asIterable() else arrayOf(it).asIterable() }) {
      val dependentVariable = session.getInferenceVariable(lowerType)
      if (dependentVariable != null && dependentVariable in nodes.keys && dependentVariable != inferenceVariable) {
        subtypes.add(nodes.getValue(dependentVariable))
        nodes.getValue(dependentVariable).supertypes.add(this)
      }
      else {
        collectBounds({
                        if (it != this) {
                          weakSubtypes.add(it);
                          it.weakSupertypes.add(this)
                        }
                      }, lowerType, session, nodes)
      }
    }
  }


  private fun collectBounds(relationHandler: (InferenceVariableNode) -> Unit,
                            type: PsiType,
                            session: GroovyInferenceSession,
                            nodes: Map<InferenceVariable, InferenceVariableNode>) {
    val typeVisitor = object : PsiTypeVisitor<PsiType>() {

      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val dependentVariable = session.getInferenceVariable(classType)
        if (dependentVariable != null && dependentVariable in nodes) {
          relationHandler(nodes.getValue(dependentVariable))
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

    type.accept(typeVisitor)
  }

  override fun toString(): String {
    return "{" + inferenceVariable.type().toString() + "}"
  }
}