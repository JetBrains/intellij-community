// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.notification.impl.NotificationSoundEP
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@TestApplication
class BreakpointHitNotificationSoundTest {
  @Test
  fun `the breakpoint hit group binds a readable sound`() {
    val ep = NotificationSoundEP.EP_NAME.extensionList.single { it.group == "Breakpoint hit" }

    val loader = checkNotNull(ep.pluginDescriptor?.pluginClassLoader)
    val stream = checkNotNull(loader.getResourceAsStream(ep.sound)) { ep.sound }
    assertNotEquals(-1, stream.use { it.read() })
  }
}
