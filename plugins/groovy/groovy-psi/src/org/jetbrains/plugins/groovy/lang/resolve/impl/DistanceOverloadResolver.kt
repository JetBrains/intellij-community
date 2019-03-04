// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyOverloadResolver

class DistanceOverloadResolver : GroovyOverloadResolver {

  override fun compare(left: GroovyMethodResult, right: GroovyMethodResult): Int {
    val leftMapping = left.candidate?.argumentMapping
    val rightMapping = right.candidate?.argumentMapping
    if (leftMapping == null && rightMapping == null) {
      return 0
    }
    else if (leftMapping == null) {
      return 1
    }
    else if (rightMapping == null) {
      return -1
    }
    else {
      return compare(leftMapping, rightMapping)
    }
  }
}
