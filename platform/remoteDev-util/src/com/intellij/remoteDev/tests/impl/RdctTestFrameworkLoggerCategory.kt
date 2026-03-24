// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tests.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RdctTestFrameworkLoggerCategory {
  const val category = "com.jetbrains.rdct.testFramework" // so the same category is used for logger across modules
}