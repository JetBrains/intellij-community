// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

@TestApplication
class NotificationsBeeperTest {
  @TestDisposable
  lateinit var testDisposable: Disposable

  private val pluginLoader = object : ClassLoader(null) {
    val requested = CopyOnWriteArrayList<String>()

    override fun getResource(name: String): URL? {
      requested.add(name)
      return null
    }
  }

  @BeforeEach
  fun bindSound() {
    val ep = NotificationSoundEP().apply {
      group = BOUND_GROUP
      sound = "sounds/bound.wav"
      setPluginDescriptor(DefaultPluginDescriptor(PluginId.getId("test.plugin"), pluginLoader))
    }
    ExtensionTestUtil.addExtensions(NotificationSoundEP.EP_NAME, listOf(ep), testDisposable)
  }

  @Test
  fun `a bound group plays its sound from the plugin that binds it`() {
    enablePlaySound(BOUND_GROUP)

    NotificationsBeeper().notify(Notification(BOUND_GROUP, "title", NotificationType.ERROR))

    assertThat(pluginLoader.requested).containsExactly("sounds/bound.wav")
  }

  @Test
  fun `a group with Play sound off plays nothing`() {
    NotificationsBeeper().notify(Notification(BOUND_GROUP, "title", NotificationType.ERROR))

    assertThat(pluginLoader.requested).isEmpty()
  }

  @Test
  fun `a binding does not apply to another group`() {
    enablePlaySound(UNBOUND_GROUP)

    NotificationsBeeper().notify(Notification(UNBOUND_GROUP, "title", NotificationType.ERROR))

    assertThat(pluginLoader.requested).isEmpty()
  }

  private fun enablePlaySound(groupId: String) {
    NotificationsConfigurationImpl.getInstanceImpl()
      .changeSettings(NotificationSettings(groupId, NotificationDisplayType.BALLOON, false, false, true))
    Disposer.register(testDisposable) { NotificationsConfigurationImpl.remove(groupId) }
  }

  private companion object {
    const val BOUND_GROUP = "NotificationsBeeperTest bound group"
    const val UNBOUND_GROUP = "NotificationsBeeperTest unbound group"
  }
}
