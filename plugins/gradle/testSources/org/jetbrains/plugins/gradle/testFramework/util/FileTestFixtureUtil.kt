// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture

fun <R> FileTestFixture.withSuppressedErrors(action: () -> R): R {
  suppressErrors(true)
  try {
    return action()
  }
  finally {
    suppressErrors(false)
  }
}
