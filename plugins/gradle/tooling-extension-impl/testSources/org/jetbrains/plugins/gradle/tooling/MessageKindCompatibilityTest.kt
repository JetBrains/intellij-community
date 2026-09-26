// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling

import com.intellij.build.events.MessageEvent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class MessageKindCompatibilityTest {

  @Test
  fun `All Gradle message Kinds can be mapped to IntelliJ Platform message Kinds`() {
    Message.Kind.entries.forEach {
      Assertions.assertNotNull(MessageEvent.Kind.valueOf(it.name),
                               "Gradle message kind '${it.name}' has no corresponding IntelliJ Platform message kind. Available kinds:\n" +
                               MessageEvent.Kind.entries.joinToString("\n") { meKinds -> meKinds.name })
    }
  }
}
