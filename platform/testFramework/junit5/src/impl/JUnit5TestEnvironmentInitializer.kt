// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.common.initializeTestEnvironment
import org.jetbrains.annotations.TestOnly
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener

@TestOnly
internal class JUnit5TestEnvironmentInitializer : LauncherSessionListener {

  override fun launcherSessionOpened(session: LauncherSession) {
    initializeTestEnvironment()
  }
}
