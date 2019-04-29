// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.util.containers.BidirectionalMap
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * Manager for [InferenceUnit]
 */
class InferenceUnitRegistry {

  private val units = ArrayList<InferenceUnit>()

  companion object Connector {
    private val weakUpperBoundHandler: (InferenceUnit, InferenceUnit) -> Unit = { upper, lower ->
      if (upper != lower) {
        lower.weakSupertypes.add(upper)
        upper.weakSubtypes.add(lower)
      }
    }
    private val weakLowerBoundHandler: (InferenceUnit, InferenceUnit) -> Unit = { upper, lower ->
      weakUpperBoundHandler(lower, upper)
    }
    private val strongUpperBoundHandler: (InferenceUnit, InferenceUnit) -> Unit = { upper, lower ->
      lower.supertypes.add(upper)
      upper.subtypes.add(lower)
    }
    private val strongLowerBoundHandler: (InferenceUnit, InferenceUnit) -> Unit = { upper, lower ->
      strongUpperBoundHandler(lower, upper)
    }
  }

  fun getUnits(): List<InferenceUnit> = units

  fun createUnit(typeParameter: PsiTypeParameter): InferenceUnit = InferenceUnit.create(typeParameter, this)

  internal fun register(unit: InferenceUnit) {
    units.add(unit)
  }

  fun setUpUnits(variables: List<InferenceVariable>, session: GroovyInferenceSession) {
    val map = BidirectionalMap<InferenceUnit, InferenceVariable>()
    for (variable in variables) {
      val unit = createUnit(variable.parameter)
      unit.typeInstantiation = variable.instantiation
      map[unit] = variable
    }
    val entries = map.entries.sortedBy { (unit, _) -> unit.toString() }
    for ((unit, variable) in entries) {
      deepConnect(session, map, (variable.getBounds(InferenceBound.UPPER) + variable.getBounds(InferenceBound.EQ)),
                  { strongUpperBoundHandler(it, unit) }, { weakUpperBoundHandler(it, unit) })
      deepConnect(session, map, (variable.getBounds(InferenceBound.LOWER) + variable.getBounds(InferenceBound.EQ)),
                  { strongLowerBoundHandler(it, unit) }, { weakLowerBoundHandler(it, unit) })
    }
  }

  private fun deepConnect(session: GroovyInferenceSession,
                          map: BidirectionalMap<InferenceUnit, InferenceVariable>,
                          typeList: List<PsiType>,
                          strongRelationHandler: (InferenceUnit) -> Unit,
                          weakRelationHandler: (InferenceUnit) -> Unit) {

    val boundVisitor = object : PsiTypeVisitor<PsiType>() {
      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val dependentVariable = session.getInferenceVariable(classType)
        if (dependentVariable in map.values) {
          weakRelationHandler(map.getKeysByValue(dependentVariable)!!.first())
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
    for (type in typeList.flattenIntersections()) {
      val dependentVariable = session.getInferenceVariable(type)
      if (dependentVariable != null) {
        strongRelationHandler(map.getKeysByValue(dependentVariable)!!.first())
      }
      else {
        type.accept(boundVisitor)
      }
    }
  }

  override fun toString(): String {
    return units.joinToString()
  }

  fun searchUnit(type: PsiType): InferenceUnit? {
    return units.firstOrNull { it.initialTypeParameter.type() == type }
  }
}