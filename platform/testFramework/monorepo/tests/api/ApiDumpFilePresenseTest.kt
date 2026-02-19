// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.platform.testFramework.monorepo.MonorepoProjectStructure
import org.junit.jupiter.api.Test

internal class ApiDumpFilePresenseTest {
  @Test
  fun `platform modules define API dump`() {
    checkModulesDefineApiDump(MonorepoProjectStructure.communityProject.modules)
  }
}