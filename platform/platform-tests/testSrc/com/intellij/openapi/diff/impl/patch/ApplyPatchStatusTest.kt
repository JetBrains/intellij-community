// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus.PARTIAL
import org.junit.Test
import kotlin.math.max
import kotlin.test.assertEquals

class ApplyPatchStatusTest {
  @Test fun checkStatusAnd() {
    ApplyPatchStatus.ORDERED_TYPES.forEach { typeA ->
      ApplyPatchStatus.ORDERED_TYPES.forEach { typeB -> checkAndFor(typeA, typeB, getResult(typeA, typeB)); }
      checkAndFor(typeA, null, typeA)
      checkAndFor(null, typeA, typeA)
    }
  }

  private fun getResult(lhs: ApplyPatchStatus?, rhs: ApplyPatchStatus?): ApplyPatchStatus? {
    if (lhs == null) return rhs
    if (rhs == null) return lhs
    if (lhs == rhs) return lhs
    if (ApplyPatchStatus.PARTIAL_ADDITIONAL_SET.containsAll(listOf(lhs, rhs))) return PARTIAL
    val index = max(ApplyPatchStatus.ORDERED_TYPES.indexOf(lhs), ApplyPatchStatus.ORDERED_TYPES.indexOf(rhs))
    return ApplyPatchStatus.ORDERED_TYPES[index]
  }

  private fun checkAndFor(statusA: ApplyPatchStatus?, statusB: ApplyPatchStatus?, expectedResult: ApplyPatchStatus?) {
    assertEquals(expectedResult, ApplyPatchStatus.and(statusA, statusB), "Bad result :$statusA $statusB")
  }
}