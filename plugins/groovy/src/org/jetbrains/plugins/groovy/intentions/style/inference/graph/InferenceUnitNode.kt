// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.INHABIT
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.TypeUsageInformation
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode.Companion.InstantiationHint.*
import org.jetbrains.plugins.groovy.intentions.style.inference.removeWildcard
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import kotlin.LazyThreadSafetyMode.NONE

/**
 * An analogue for inference variable.
 *
 * Unit is [direct] if it should be instantiated directly to it's supertype.
 */
class InferenceUnitNode internal constructor(val core: InferenceUnit,
                                             parents: Set<() -> InferenceUnitNode>,
                                             children: Set<() -> InferenceUnitNode>,
                                             val typeInstantiation: PsiType,
                                             val direct: Boolean = false) {

  companion object {
    enum class InstantiationHint {
      REIFIED_AS_TYPE_PARAMETER,
      REIFIED_AS_PROPER_TYPE,
      ENDPOINT_TYPE_PARAMETER,
      NEW_TYPE_PARAMETER,
      EXTENDS_WILDCARD
    }
  }

  /**
   * direct supertypes of [InferenceUnit.initialTypeParameter]
   */
  val supertypes: Set<InferenceUnitNode> by lazy { parents.map { it() }.toSet() }

  /**
   * direct subtypes of [InferenceUnit.initialTypeParameter]
   */
  val subtypes: Set<InferenceUnitNode> by lazy { children.map { it() }.toSet() }

  val type = core.type

  fun smartTypeInstantiation(usage: TypeUsageInformation,
                             equivalenceClasses: Map<PsiType, List<InferenceUnitNode>>): Pair<PsiType, InstantiationHint> {

    if (core.constant) {
      return if (core.initialTypeParameter.extendsListTypes.isEmpty()) {
        typeInstantiation to NEW_TYPE_PARAMETER
      }
      else {
        type to REIFIED_AS_TYPE_PARAMETER
      }
    }

    if (parent == null) {
      if (typeInstantiation == PsiType.NULL) {
        return PsiWildcardType.createUnbounded(core.initialTypeParameter.manager) to REIFIED_AS_PROPER_TYPE
      }
      if (typeInstantiation.resolve()?.hasModifierProperty("final") == true) {
        return typeInstantiation to REIFIED_AS_PROPER_TYPE
      }
      val flushedTypeInstantiation = removeWildcard(typeInstantiation)
      if (flushedTypeInstantiation is PsiIntersectionType) {
        return flushedTypeInstantiation to NEW_TYPE_PARAMETER
      }
    }

    if (parent != null) {
      if (!usage.contravariantTypes.contains(core.initialTypeParameter.type()) && subtypes.isEmpty()) {
        return parent!!.type to EXTENDS_WILDCARD
      }
      else {
        return parent!!.type to NEW_TYPE_PARAMETER
      }
    }

    val inhabitedByUniqueType = lazy(NONE) {
      usage.requiredClassTypes[core.initialTypeParameter]?.filter { it.marker == INHABIT }?.run { isNotEmpty() && all { first().type == it.type } }
      ?: false
    }
    if (equivalenceClasses[type]?.all { it.core.initialTypeParameter !in usage.dependentTypes } == true) {
      if (direct || typeInstantiation is PsiWildcardType || inhabitedByUniqueType.value) {
        return typeInstantiation to REIFIED_AS_PROPER_TYPE
      }
      else {
        return typeInstantiation to EXTENDS_WILDCARD
      }
    }

    if (equivalenceClasses[type]?.any { usage.invariantTypes.contains(it.type) || it.subtypes.isNotEmpty() } == true) {
      val advice = parent?.type ?: typeInstantiation
      return if (advice == typeInstantiation) {
        (if (advice == PsiType.NULL) type else advice) to ENDPOINT_TYPE_PARAMETER
      }
      else {
        advice to NEW_TYPE_PARAMETER
      }
    }

    if (direct) {
      return typeInstantiation to REIFIED_AS_PROPER_TYPE
    }
    if (typeInstantiation == PsiType.NULL) {
      return type to REIFIED_AS_TYPE_PARAMETER
    }
    return if (subtypes.isNotEmpty()) {
      type to REIFIED_AS_TYPE_PARAMETER
    }
    else {
      typeInstantiation to REIFIED_AS_PROPER_TYPE
    }

  }

  /**
   * Direct dependency on other unit.
   * It appears when we know that this [InferenceUnit.initialTypeParameter] must be a subtype of some other one.
   * In java code it might be expressed as `<T extends U> void foo(){}`, so unit for `T` will have [parent] set to `U`
   */
  val parent: InferenceUnitNode? by lazy { supertypes.firstOrNull() }

  override fun toString(): String {
    return "Unit node: ${core.initialTypeParameter.type().canonicalText} (${typeInstantiation.presentableText})"
  }
}