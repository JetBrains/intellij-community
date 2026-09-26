// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiType

interface ApplicabilityResult {

  /**
   * Applicability of the whole argument list
   */
  val applicability: Applicability

  /**
   * Applicability of each argument separately
   */
  val argumentApplicabilities: Map<Argument, ArgumentApplicability>

  class ArgumentApplicability(val expectedType: PsiType?, val applicability: Applicability)

  object Applicable : ApplicabilityResult {
    override val applicability: Applicability get() = Applicability.applicable
    override val argumentApplicabilities: Map<Argument, ArgumentApplicability> get() = emptyMap()
  }

  object Inapplicable : ApplicabilityResult {
    override val applicability: Applicability get() = Applicability.inapplicable
    override val argumentApplicabilities: Map<Argument, ArgumentApplicability> get() = emptyMap()
  }
}
