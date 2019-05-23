// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.util.containers.BidirectionalMap
import org.jetbrains.plugins.groovy.intentions.style.inference.ensureWildcards
import org.jetbrains.plugins.groovy.intentions.style.inference.flattenIntersections
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class InferenceUnitGraphBuilder {

  private val relations: MutableList<Relation> = mutableListOf()
  private val registered: MutableSet<InferenceUnit> = mutableSetOf()
  private val fixedUnits: MutableSet<InferenceUnit> = mutableSetOf()
  private val fixedInstantiations: MutableMap<InferenceUnit, PsiType> = mutableMapOf()
  private val directUnits: MutableSet<InferenceUnit> = mutableSetOf()

  companion object {
    fun createGraphFromInferenceVariables(variables: List<InferenceVariable>,
                                          session: GroovyInferenceSession,
                                          flexibleTypes: Collection<PsiType> = emptySet(),
                                          constantTypes: Collection<PsiType> = emptySet(),
                                          forbiddingTypes: Collection<PsiType> = emptySet(),
                                          appearedClassTypes: Map<String, List<PsiClass?>>): InferenceUnitGraph {
      val map = BidirectionalMap<InferenceUnit, InferenceVariable>()
      val builder = InferenceUnitGraphBuilder()
      for (variable in variables) {
        val typeParameter = variable.parameter.type()
        val parameterTypes = when {
          variable.parameter.extendsList.referencedTypes.size <= 1 -> variable.parameter.extendsList.referencedTypes.toList()
          else -> variable.parameter.extendsList.referencedTypes.filter {
            appearedClassTypes[typeParameter.className]?.contains(it.resolve()) ?: false
          }
        }
        val instantiation = variable.instantiation
        val partialInstantiation = when {
          instantiation is PsiIntersectionType &&
          instantiation.conjuncts.map { it.canonicalText }.contains(GroovyCommonClassNames.GROOVY_OBJECT)
          && instantiation.conjuncts.size == 2 -> (instantiation.conjuncts.asIterable() - (instantiation.conjuncts.find { it.canonicalText == GroovyCommonClassNames.GROOVY_OBJECT })).first()
          else -> instantiation
        }
        val validType = when {
          parameterTypes.size > 1 -> PsiIntersectionType.createIntersection(
            parameterTypes.toList())
          // questionable condition. I guess, we can allow LUB instead of Object if variable will instantiate into a parametrized type.
          parameterTypes.isEmpty() && partialInstantiation is PsiIntersectionType -> PsiType.getJavaLangObject(session.manager,
                                                                                                               session.scope)
          else -> parameterTypes.firstOrNull() ?: partialInstantiation!!.ensureWildcards(
            GroovyPsiElementFactory.getInstance(variable.project), variable.manager)
        }
        val core = InferenceUnit(variable.parameter,
                                 typeParameter in flexibleTypes,
                                 typeParameter in constantTypes)
        builder.register(core)
        builder.setType(core, validType)
        if (typeParameter in forbiddingTypes && variable.instantiation.equalsToText("java.lang.Object")) {
          builder.forbidInstantiation(core)
        }
        map[core] = variable
      }

      val entries = map.entries.sortedBy { (unit, _) -> unit.toString() }
      for ((unit, variable) in entries) {
        deepConnect(session, map, (variable.getBounds(InferenceBound.UPPER) + variable.getBounds(InferenceBound.EQ))
        ) { builder.add(it, unit, RelationType.SUPER) }
        deepConnect(session, map, (variable.getBounds(InferenceBound.LOWER) + variable.getBounds(InferenceBound.EQ))
        ) { builder.add(it, unit, RelationType.SUB) }
      }
      return builder.build()
    }

    private fun deepConnect(session: GroovyInferenceSession,
                            map: BidirectionalMap<InferenceUnit, InferenceVariable>,
                            typeList: List<PsiType>,
                            relationHandler: (InferenceUnit) -> Unit) {

      val boundVisitor = object : PsiTypeVisitor<PsiType>() {
        override fun visitClassType(classType: PsiClassType?): PsiType? {
          classType ?: return classType
          classType.parameters.forEach { it.accept(this) }
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
        if (dependentVariable != null && dependentVariable in map.values) {
          relationHandler(map.getKeysByValue(dependentVariable)!!.first())
        }
        else {
          type.accept(boundVisitor)
        }
      }
    }
  }

  fun add(left: InferenceUnit, right: InferenceUnit, type: RelationType): InferenceUnitGraphBuilder {
    register(left)
    register(right)
    relations.add(Relation(left, right, type))
    relations.add(Relation(right, left, type.complement()))
    return this
  }

  fun register(unit: InferenceUnit): InferenceUnitGraphBuilder {
    registered.add(unit)
    return this
  }

  fun register(unitNode: InferenceUnitNode): InferenceUnitGraphBuilder {
    register(unitNode.core)
    if (unitNode.direct) {
      setDirect(unitNode.core)
    }
    if (unitNode.forbidInstantiation) {
      forbidInstantiation(unitNode.core)
    }
    setType(unitNode.core, unitNode.typeInstantiation)
    return this
  }

  fun forbidInstantiation(unit: InferenceUnit): InferenceUnitGraphBuilder {
    register(unit)
    fixedUnits.add(unit)
    return this
  }

  fun setDirect(unit: InferenceUnit): InferenceUnitGraphBuilder {
    register(unit)
    directUnits.add(unit)
    return this
  }

  fun setType(unit: InferenceUnit, type: PsiType): InferenceUnitGraphBuilder {
    register(unit)
    fixedInstantiations[unit] = type
    return this
  }


  fun build(): InferenceUnitGraph {
    val inferenceNodes = ArrayList<InferenceUnitNode>()
    val superTypesMap: MutableList<MutableSet<() -> InferenceUnitNode>> = mutableListOf()
    val subTypesMap: MutableList<MutableSet<() -> InferenceUnitNode>> = mutableListOf()
    //todo: bug with order
    val registeredUnits = registered.sortedBy { it.initialTypeParameter.name }
    repeat(registeredUnits.size) {
      superTypesMap.add(mutableSetOf())
      subTypesMap.add(mutableSetOf())
    }
    val unitIndexMap: Map<InferenceUnit, Int> = registeredUnits.zip(registeredUnits.indices).toMap()
    for ((left, right, type) in relations) {
      when (type) {
        RelationType.SUPER -> subTypesMap[unitIndexMap.getValue(left)].add { inferenceNodes[unitIndexMap.getValue(right)] }
        RelationType.SUB -> superTypesMap[unitIndexMap.getValue(left)].add { inferenceNodes[unitIndexMap.getValue(right)] }
      }
    }
    for (index in registeredUnits.indices) {
      val unit = registeredUnits[index]
      inferenceNodes.add(InferenceUnitNode(unit, superTypesMap[index], subTypesMap[index],
                                           fixedInstantiations[unit]
                                           ?: PsiType.NULL,
                                           unit in fixedUnits,
                                           unit in directUnits))
    }
    return InferenceUnitGraph(inferenceNodes)
  }

}