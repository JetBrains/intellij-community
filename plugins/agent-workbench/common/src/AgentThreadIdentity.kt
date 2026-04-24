// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

private val PROVIDER_ID_REGEX = Regex("[a-z][a-z0-9._-]*")

data class AgentThreadIdentity(
  @JvmField val providerId: String,
  @JvmField val threadId: String,
)

fun buildAgentThreadIdentity(providerId: String, threadId: String): String {
  require(PROVIDER_ID_REGEX.matches(providerId)) {
    "Invalid provider id '$providerId'. Expected: ${PROVIDER_ID_REGEX.pattern}"
  }
  require(threadId.isNotBlank()) {
    "Thread id must not be blank"
  }
  return "$providerId:$threadId"
}

fun parseAgentThreadIdentity(identity: String): AgentThreadIdentity? {
  val separator = identity.indexOf(':')
  if (separator <= 0 || separator == identity.lastIndex) {
    return null
  }

  val providerId = identity.substring(0, separator)
  if (!PROVIDER_ID_REGEX.matches(providerId)) {
    return null
  }

  val threadId = identity.substring(separator + 1)
  if (threadId.isBlank()) {
    return null
  }

  return AgentThreadIdentity(providerId = providerId, threadId = threadId)
}
