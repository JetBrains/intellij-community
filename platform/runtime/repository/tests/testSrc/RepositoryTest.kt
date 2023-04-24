// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository

import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class RepositoryTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun `two modules`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("intellij.platform.util.rt", listOf("../util-rt.jar"), emptyList()),
      RawRuntimeModuleDescriptor("intellij.platform.util", emptyList(), listOf("intellij.platform.util.rt")),
    )
    val util = repository.getModule(RuntimeModuleId.module("intellij.platform.util"))
    val utilRt = repository.getModule(RuntimeModuleId.module("intellij.platform.util.rt"))
    assertEquals(listOf(utilRt), util.dependencies)
  }
}