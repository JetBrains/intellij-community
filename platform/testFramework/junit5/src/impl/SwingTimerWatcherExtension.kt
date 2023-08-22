// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.common.checkJavaSwingTimersAreDisposed
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@TestOnly
internal class SwingTimerWatcherExtension : AfterEachCallback {

  override fun afterEach(context: ExtensionContext?) {
    checkJavaSwingTimersAreDisposed()
  }
}
