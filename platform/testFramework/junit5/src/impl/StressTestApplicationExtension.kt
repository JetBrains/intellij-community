// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.application.ex.ApplicationManagerEx
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

@TestOnly
internal class StressTestApplicationExtension : BeforeAllCallback, AfterAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    ApplicationManagerEx.setInStressTest(true)
  }

  override fun afterAll(context: ExtensionContext) {
    ApplicationManagerEx.setInStressTest(false)
  }
}
