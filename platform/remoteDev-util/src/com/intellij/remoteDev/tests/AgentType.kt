package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class AgentType {
  CLIENT, HOST, GATEWAY;
}