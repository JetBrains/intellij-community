package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object AgentConstants {
  const val protocolName = "DistributedTestProtocol"
  const val protocolPortEnvVar = "CODE_WITH_ME_TESTING_PORT"
  const val threadDumpFilePrefix = "threadDump"
  // host.docker.internal is not available on linux yet (20.04+)
  const val dockerHostIpEnvVar = "CODE_WITH_ME_DOCKER_HOST"
}