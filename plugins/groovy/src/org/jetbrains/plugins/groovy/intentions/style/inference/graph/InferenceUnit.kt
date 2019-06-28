// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * Wrapper for [PsiTypeParameter]
 * Unit is [constant] if must not be changed.
 * For example, we knew before inference process that some type parameters were already existed.
 * We need units for these type parameters, but their instantiation must not change.
 *
 * Unit is [flexible] if it allows direct instantiation to it's non-variable type avoiding wildcards.
 * For example:
 * `def <T, U> foo(List<U> us, T ts){}`. Here is T might be flexible and U not.
 */
data class InferenceUnit(val initialTypeParameter: PsiTypeParameter,
                         val flexible: Boolean = false,
                         val constant: Boolean = false) {
  val type = initialTypeParameter.type()
  override fun toString(): String = "Inference unit: ${initialTypeParameter.type().canonicalText}"
}