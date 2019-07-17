// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * An analogue for inference variable.
 *
 * Unit is [direct] if it should be instantiated directly to it's supertype.
 */
class InferenceUnitNode internal constructor(val core: InferenceUnit,
                                             parents: Set<() -> InferenceUnitNode>,
                                             children: Set<() -> InferenceUnitNode>,
                                             val typeInstantiation: PsiType,
                                             val forbiddenToInstantiate: Boolean = false,
                                             val direct: Boolean = false) {

  companion object {
    enum class InstantiationHint {
      REIFIED_AS_TYPE_PARAMETER,
      REIFIED_AS_PROPER_TYPE,
      ENDPOINT_TYPE_PARAMETER,
      NEW_TYPE_PARAMETER,
      WILDCARD
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

  fun smartTypeInstantiation(): Pair<PsiType, InstantiationHint> {

    if (core.constant) {
      return if (core.initialTypeParameter.extendsListTypes.isEmpty()) {
        typeInstantiation to InstantiationHint.NEW_TYPE_PARAMETER
      }
      else {
        type to InstantiationHint.REIFIED_AS_TYPE_PARAMETER
      }
    }

    if ((core.flexible && typeInstantiation is PsiIntersectionType) || forbiddenToInstantiate) {
      val advice = parent?.type ?: typeInstantiation
      return if (advice == typeInstantiation) {
        advice to InstantiationHint.ENDPOINT_TYPE_PARAMETER
      }
      else {
        advice to InstantiationHint.NEW_TYPE_PARAMETER
      }
    }

    if ((core.flexible && typeInstantiation !is PsiIntersectionType) || direct) {
      return typeInstantiation to InstantiationHint.REIFIED_AS_PROPER_TYPE
    }

    return when {
      typeInstantiation == type || typeInstantiation == PsiType.NULL || typeInstantiation.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ->
        PsiType.NULL to InstantiationHint.WILDCARD
      else -> typeInstantiation to InstantiationHint.WILDCARD
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