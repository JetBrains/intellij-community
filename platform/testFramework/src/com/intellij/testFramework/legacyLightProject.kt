// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * Closes the shared light project of [LightPlatformTestCase], if it is open.
 *
 * A JUnit 3 light test can leave this project open after it ends.
 * A JUnit 5 test of the same JVM calls this function, so that its project leak check does not report that project.
 * The caller does not need JUnit 4 on its classpath.
 */
@TestOnly
@Suppress("DEPRECATION")
suspend fun closeLegacyLightProject() {
  withContext(Dispatchers.EDT) {
    LightPlatformTestCase.closeAndDeleteProject()
  }
}
