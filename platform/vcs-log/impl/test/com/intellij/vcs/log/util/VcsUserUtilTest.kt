// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util

import com.intellij.openapi.util.Couple
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VcsUserUtilTest {

  @Test
  fun `name with macron`() {
    assertNull(VcsUserUtil.getFirstAndLastName("Abcūdef"))
  }

  @Test
  fun `name with turkish letter I`() {
    assertNull(VcsUserUtil.getFirstAndLastName("Abcİdef"))
  }

  @Test
  fun `name with umlauts`() {
    assertNull(VcsUserUtil.getFirstAndLastName("Abäcödüef"))
  }

  @Test
  fun `name and surname separated by punctuation`() {
    for (c in "\"#\$%&()*+,-./:;=?@[]^_`{|}~ ") {
      assertEquals(Couple.of("Ivan", "Ivanov"), VcsUserUtil.getFirstAndLastName("Ivan${c}Ivanov"),
                   "Incorrect first and last name for separator character '${c}'")
    }
  }
}