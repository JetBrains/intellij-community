// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import org.junit.Assert.*
import org.junit.Test

class ProjectOriginTest {

  @Test
  fun `origin from url`() {
    val tests = listOf(
      "https://github.com/JetBrains/intellij.git" to "github.com/JetBrains",
      "ssh://git@github.com:JetBrains/intellij.git" to "github.com/JetBrains",
      "git@github.com:JetBrains/intellij.git" to "github.com/JetBrains",
      "ssh://git@git.example.com/intellij.git" to "git.example.com",
      "https://192.168.1.1/project.git" to "192.168.1.1"
    )
    for ((url, expected) in tests) {
      assertEquals("Incorrectly parsed $url", expected, getOriginFromUrl(url)?.host)
    }
  }
}