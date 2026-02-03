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