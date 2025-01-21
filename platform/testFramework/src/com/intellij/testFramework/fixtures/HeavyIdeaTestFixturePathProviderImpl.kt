// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures

import com.intellij.openapi.Disposable
import java.nio.file.Path

/**
 * The default implementation for [HeavyIdeaTestFixturePathProvider].
 */
class HeavyIdeaTestFixturePathProviderImpl : HeavyIdeaTestFixturePathProvider {
  override fun get(testName: String, disposable: Disposable): Path? = null
}