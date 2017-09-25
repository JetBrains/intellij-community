/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.containers.hash

import com.intellij.util.containers.ContainerUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ConcurrentHashSetTest {
  @Test
  fun testEquals() {
    val set = ContainerUtil.newConcurrentSet<String>()
    assertThat(set).isNotEqualTo(emptyMap<String, String>())
    assertThat(set).isEqualTo(emptySet<String>())
    assertThat(set).isEqualTo(ContainerUtil.newConcurrentSet<String>())

    set.add("foo")
    assertThat(set).isNotEqualTo(emptySet<String>())
    assertThat(set).isEqualTo(setOf("foo"))
    assertThat(set).isNotEqualTo(setOf("bar"))

    val otherSet = ContainerUtil.newConcurrentSet<String>()
    otherSet.add("bar")
    assertThat(set).isNotEqualTo(otherSet)

    otherSet.remove("bar")
    otherSet.add("foo")
    assertThat(set).isEqualTo(otherSet)
  }
}