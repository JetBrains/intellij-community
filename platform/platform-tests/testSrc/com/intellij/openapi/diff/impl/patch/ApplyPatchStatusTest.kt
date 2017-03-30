package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus.PARTIAL
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ApplyPatchStatusTest {

  @Test fun checkStatusAnd() {
    ApplyPatchStatus.ORDERED_TYPES.forEach { typeA ->
      ApplyPatchStatus.ORDERED_TYPES.forEach { typeB -> checkAndFor(typeA, typeB, getResult(typeA, typeB)); }
      checkAndFor(typeA, null, typeA);
      checkAndFor(null, typeA, typeA);
    }
  }

  private fun getResult(lhs: ApplyPatchStatus?, rhs: ApplyPatchStatus?): ApplyPatchStatus? {
    if (lhs == null) return rhs;
    if (rhs == null) return lhs;
    if (lhs == rhs) return lhs;
    if (ApplyPatchStatus.PARTIAL_ADDITIONAL_SET.containsAll(Arrays.asList(lhs, rhs))) return PARTIAL;
    var index = Math.max(ApplyPatchStatus.ORDERED_TYPES.indexOf(lhs), ApplyPatchStatus.ORDERED_TYPES.indexOf(rhs));
    return ApplyPatchStatus.ORDERED_TYPES[index];
  }

  private fun checkAndFor(statusA: ApplyPatchStatus?, statusB: ApplyPatchStatus?, expectedResult: ApplyPatchStatus?) {
    assertEquals(expectedResult, ApplyPatchStatus.and(statusA, statusB), "Bad result :$statusA $statusB");
  }
}