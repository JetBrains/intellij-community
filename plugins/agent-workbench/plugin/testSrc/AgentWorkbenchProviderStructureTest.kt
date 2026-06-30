// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.plugin

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchProviderStructureTest {
  @Test
  fun sessionProviderExtensionIdsMatchDescriptorProviderIds() {
    val registeredIds = LinkedHashSet<String>()

    SESSION_PROVIDER_EP.filterableLazySequence().forEach { extension ->
      val extensionId = extension.id
      assertNotNull(extensionId) {
        "sessionProvider registration ${extension.implementationClassName} must declare an XML id"
      }

      val descriptor = extension.instance
      assertNotNull(descriptor) {
        "sessionProvider registration $extensionId (${extension.implementationClassName}) did not instantiate"
      }

      registeredIds += extensionId!!
      assertThat(extensionId)
        .describedAs("sessionProvider XML id must match descriptor.provider for ${extension.implementationClassName}")
        .isEqualTo(descriptor!!.provider.value)
    }

    for (providerId in BUILT_IN_PROVIDER_IDS) {
      assertTrue(providerId in registeredIds, "Built-in session provider '$providerId' is not registered")
    }
  }
}

private val SESSION_PROVIDER_EP: ExtensionPointName<AgentSessionProviderDescriptor> =
  ExtensionPointName("com.intellij.agent.workbench.sessionProvider")

private val BUILT_IN_PROVIDER_IDS = setOf(
  "codex",
  "claude",
  "junie",
  "opencode",
  "pi",
  "terminal",
)
