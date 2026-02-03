// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.totalApplicability
import org.jetbrains.plugins.groovy.lang.resolve.api.ApplicabilityResult
import org.jetbrains.plugins.groovy.lang.resolve.api.ApplicabilityResult.ArgumentApplicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument

class ApplicabilityResultImpl(override val argumentApplicabilities: Map<Argument, ArgumentApplicability>) : ApplicabilityResult {

  override val applicability: Applicability = totalApplicability(argumentApplicabilities.map { it.value.applicability })
}
