// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * @author knisht
 */

/**
 * An analogue for inference variable.
 * This class is a wrapper for [PsiTypeParameter] and widely used in inference process.
 */
class InferenceUnit private constructor(val initialTypeParameter: PsiTypeParameter) {

  companion object {
    fun create(typeParameter: PsiTypeParameter, registry: InferenceUnitRegistry): InferenceUnit {
      val unit = InferenceUnit(typeParameter)
      registry.register(unit)
      return unit
    }
  }

  /**
   * direct supertypes of [initialTypeParameter]
   */
  val supertypes: MutableSet<InferenceUnit> = LinkedHashSet()

  /**
   * direct subtypes of [initialTypeParameter]
   */
  val subtypes: MutableSet<InferenceUnit> = LinkedHashSet()

  /**
   * if [initialTypeParameter] is extends some parametrized class type, so these parameters will be in [weakSubtypes]
   */
  val weakSubtypes: MutableSet<InferenceUnit> = LinkedHashSet()
  val weakSupertypes: MutableSet<InferenceUnit> = LinkedHashSet()
  val type = initialTypeParameter.type()

  /**
   * Endpoint type instantiation for this unit.
   */
  var typeInstantiation: PsiType = PsiType.NULL

  /**
   * Direct dependency on other unit.
   * If appears when we know that this [initialTypeParameter] must be a subtype of some other one.
   * In java code it might be expressed as `<T extends U> void foo(){}`, so unit for `T` will have [unitInstantiation] set to `U`
   */
  var unitInstantiation: InferenceUnit? = null

  /**
   * Unit is constant if must not be changed.
   * For example, we knew before inference process that some type parameters were already existed.
   * We need units for these type parameters, but their instantiation must not change.
   */
  var constant: Boolean = false

  /**
   * Unit is flexible if it represents direct method parameter.
   * For example:
   * `def <T, U> foo(List<U> us, T ts){}`. Here is T flexible and U not.
   */
  var flexible: Boolean = false

  var forbidInstantiation: Boolean = false

  override fun toString(): String {
    return "Inference unit: " + initialTypeParameter.type().canonicalText
  }
}