package com.intellij.platform.testFramework.monorepo.api

import com.intellij.platform.testFramework.monorepo.MonorepoProjectStructure
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.junit.jupiter.api.*

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
