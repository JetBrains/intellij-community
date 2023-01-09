package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application

val Application.isDistributedTestMode by lazy {
  System.getenv(AgentConstants.protocolPortEnvVar)?.toIntOrNull() != null
}