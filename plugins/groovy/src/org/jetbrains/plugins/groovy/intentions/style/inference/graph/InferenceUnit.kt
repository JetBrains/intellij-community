// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * Wrapper for [PsiTypeParameter]
 * Unit is [constant] if must not be changed.
 * For example, we knew before inference process that some type parameters were already existed.
 * We need units for these type parameters, but their instantiation must not change.
 */
data class InferenceUnit(val initialTypeParameter: PsiTypeParameter,
                         val constant: Boolean = false) {
  val type = initialTypeParameter.type()
  override fun toString(): String = "Inference unit: ${initialTypeParameter.type().canonicalText}"
}