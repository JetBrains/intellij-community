/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus.PARTIAL
import org.junit.Test
import java.util.*
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
    if (ApplyPatchStatus.PARTIAL_ADDITIONAL_SET.containsAll(Arrays.asList(lhs, rhs))) return PARTIAL
    val index = Math.max(ApplyPatchStatus.ORDERED_TYPES.indexOf(lhs), ApplyPatchStatus.ORDERED_TYPES.indexOf(rhs))
    return ApplyPatchStatus.ORDERED_TYPES[index]
  }

  private fun checkAndFor(statusA: ApplyPatchStatus?, statusB: ApplyPatchStatus?, expectedResult: ApplyPatchStatus?) {
    assertEquals(expectedResult, ApplyPatchStatus.and(statusA, statusB), "Bad result :$statusA $statusB")
  }
}