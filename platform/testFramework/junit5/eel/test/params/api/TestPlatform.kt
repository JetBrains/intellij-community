// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.execution.Platform

enum class TestPlatform(internal val eelPlatform: Platform?) {
  POSIX(Platform.UNIX), WINDOWS(Platform.WINDOWS), ANY(null)
}
