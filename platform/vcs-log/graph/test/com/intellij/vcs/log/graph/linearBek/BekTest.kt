/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.linearBek

import com.intellij.vcs.log.graph.TestGraphBuilder
import org.junit.Test

class BekTest {
  fun runTest(beforeBekBuilder: TestGraphBuilder.() -> Unit, afterBekBuilder: TestGraphBuilder.() -> Unit) {
    assertEquals(afterBekBuilder, runBek(beforeBekBuilder))
  }

  @Test fun ea68497() = runTest({
    0(1, 3)
    1(4, 2)
    2(4, 3)
    3()
    4()
  }, {
    0(1, 3)
    1(4, 2)
    2(4, 3)
    3()
    4()
  })
}

