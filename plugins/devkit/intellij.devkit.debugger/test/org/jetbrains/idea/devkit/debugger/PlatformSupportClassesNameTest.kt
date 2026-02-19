// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Assert
import java.lang.reflect.Modifier

class PlatformSupportClassesNameTest : HeavyPlatformTestCase() {
  fun testApplicationDebugSupportMethodExists() {
    val clazz = Class.forName("com.intellij.ide.debug.ApplicationStateDebugSupport")
    Assert.assertNotNull(clazz.getMethod("getApplicationState"))
  }

  fun testApplicationStateFields() {
    val clazz = Class.forName("com.intellij.ide.debug.ApplicationDebugState")
    val fields = clazz.declaredFields.joinToString("\n") { "${it.type} ${it.name}" }
    assertEquals("""
      boolean readActionAllowed
      boolean writeActionAllowed
    """.trimIndent(), fields)
  }

  fun testCancellationMethodExists() {
    val clazz = Class.forName("com.intellij.openapi.progress.Cancellation")
    Assert.assertNotNull(clazz.getDeclaredMethod("initThreadNonCancellableState"))
  }

  fun testCancellationStatusFields() {
    val clazz = Class.forName("com.intellij.openapi.progress.Cancellation\$DebugNonCancellableState")
    val fields = clazz.declaredFields
      .filter { (it.modifiers and Modifier.STATIC) == 0 }
      .joinToString("\n") { "${it.type} ${it.name}" }
    assertEquals("""
      boolean inNonCancelableSection
    """.trimIndent(), fields)
  }
}
