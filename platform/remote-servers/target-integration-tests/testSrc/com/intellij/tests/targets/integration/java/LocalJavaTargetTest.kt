// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests.targets.integration.java

import com.intellij.tests.targets.java.JavaTargetTestBase
import com.intellij.util.io.write
import java.util.*

class LocalJavaTargetTest : JavaTargetTestBase() {
  override val targetName: String? = null

  override lateinit var targetFilePath: String

  override lateinit var targetFileContent: String

  override fun setUp() {
    super.setUp()
    val file = tempDir.newPath("localJavaTargetTest")
    targetFilePath = file.toString()
    targetFileContent = UUID.randomUUID().toString()
    file.write(targetFileContent)
  }
}