// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class CoroutineDumpTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun enableDumps() {
      enableCoroutineDump()
    }
  }

  @OptIn(InternalCoroutinesApi::class)
  @Suppress("SSBasedInspection", "DEPRECATION_ERROR")
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  fun testRecursiveJobsDump() {
    val projectScope = CoroutineScope(CoroutineName("Project"))
    val pluginScope = CoroutineScope(CoroutineName("Plugin"))
    val activity = projectScope.childScope("Project Activity")
    pluginScope.coroutineContext[Job]!!.attachChild(activity.coroutineContext[Job]!! as ChildJob)
    // e.g. bug here
    activity.coroutineContext[Job]!!.attachChild(pluginScope.coroutineContext[Job]!! as ChildJob)
    assertEquals("""
- JobImpl{Active}
	- "Project Activity":supervisor:ChildScope{Active}
		- JobImpl{Active}
			- CIRCULAR REFERENCE: "Project Activity":supervisor:ChildScope{Active}
""", dumpCoroutines(projectScope, true, true))
    assertEquals("""
- JobImpl{Active}
	- "Project Activity":supervisor:ChildScope{Active}
		- CIRCULAR REFERENCE: JobImpl{Active}
""", dumpCoroutines(pluginScope, true, true))
    projectScope.cancel()
    pluginScope.cancel()
  }
}