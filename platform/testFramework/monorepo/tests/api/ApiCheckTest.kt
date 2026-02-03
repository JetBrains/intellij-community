// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.platform.testFramework.monorepo.MonorepoProjectStructure
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class ApiCheckTest {

  companion object {

    @OptIn(DelicateCoroutinesApi::class)
    private val cs: CoroutineScope = GlobalScope.childScope("ApiCheckTest")

    @AfterAll
    @JvmStatic
    fun afterAll() {
      cs.cancel()
    }
  }

  @TestFactory
  fun api(): List<DynamicTest> = performApiCheckTest(cs, MonorepoProjectStructure.communityProject.modules)
}
