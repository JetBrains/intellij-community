// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object LambdaTestsConstants {
  const val protocolName = "LambdaTestProtocol"
  const val protocolHostPropertyName = "LAMBDA_TESTING_HOST"
  const val protocolPortPropertyName = "LAMBDA_TESTING_PORT"
  const val threadDumpFileSubstring = "threadDump"
  const val sourcePathProperty = "idea.sources.path"
}