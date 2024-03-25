// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.application

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.cleanApplicationState
import com.intellij.testFramework.junit5.impl.testApplication
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * @see com.intellij.testFramework.junit5.impl.TestApplicationExtension
 */
@TestOnly
class GradleTestApplicationExtension : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    context.testApplication().getOrThrow()
  }

  override fun afterAll(context: ExtensionContext) {
    ApplicationManager.getApplication().cleanApplicationState()
  }
}