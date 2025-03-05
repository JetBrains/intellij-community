package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DistributedTestsAgentConstants {
  const val protocolName = "DistributedTestProtocol"
  const val protocolHostPropertyName = "RDCT_TESTING_HOST"
  const val protocolPortPropertyName = "RDCT_TESTING_PORT"
  const val threadDumpFileSubstring = "threadDump"
}