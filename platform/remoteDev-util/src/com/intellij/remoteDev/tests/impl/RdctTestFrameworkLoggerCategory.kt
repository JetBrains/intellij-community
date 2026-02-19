package com.intellij.remoteDev.tests.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RdctTestFrameworkLoggerCategory {
  const val category = "com.jetbrains.rdct.testFramework" // so the same category is used for logger across modules
}