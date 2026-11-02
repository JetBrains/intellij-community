// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.notification.impl.NotificationGroupEP
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

private const val GROUP_ID = "NotificationGroupLazyTitleTest"
private const val BUNDLE_PATH = "messages/NotificationGroupLazyTitleTestBundle"

@TestApplication
internal class NotificationGroupLazyTitleTest {
  @Test
  fun `group title loads its resource bundle on the first read`(@TestDisposable disposable: Disposable) {
    val classLoader = BundleClassLoader("group.title=Lazy group title")
    val manager = NotificationGroupManager.getInstance()

    registerGroup(classLoader, disposable)

    assertThat(manager.isGroupRegistered(GROUP_ID)).isTrue()
    assertThat(manager.getNotificationGroup(GROUP_ID)).isNotNull()
    assertThat(classLoader.bundleRequests.get()).isZero()

    assertThat(NotificationGroup.getGroupTitle(GROUP_ID)).isEqualTo("Lazy group title")
    val requestsAfterFirstRead = classLoader.bundleRequests.get()
    assertThat(requestsAfterFirstRead).isPositive()

    assertThat(NotificationGroup.getGroupTitle(GROUP_ID)).isEqualTo("Lazy group title")
    assertThat(classLoader.bundleRequests.get()).isEqualTo(requestsAfterFirstRead)
  }

  @Test
  fun `canShowFor does not read the group title`(@TestDisposable disposable: Disposable) {
    val classLoader = BundleClassLoader("group.title=Lazy group title")
    registerGroup(classLoader, disposable)
    val doNotAskManager = DoNotAskAppManager.getInstance()
    try {
      val notification = Notification(GROUP_ID, "Title", "Content", NotificationType.INFORMATION)
      assertThat(notification.canShowFor(null)).isTrue()
      assertThat(classLoader.bundleRequests.get()).isZero()

      notification.setDoNotAskFor(null)
      assertThat(classLoader.bundleRequests.get()).isPositive()
      assertThat(doNotAskManager.getDoNotAskNotifications()[GROUP_ID]).isEqualTo("Lazy group title")
      assertThat(notification.canShowFor(null)).isFalse()
    }
    finally {
      doNotAskManager.clearDoNotAsk(GROUP_ID)
    }
  }

  private fun registerGroup(classLoader: ClassLoader, disposable: Disposable) {
    val element = JDOMUtil.load("""<notificationGroup id="$GROUP_ID" displayType="BALLOON"
                                                   bundle="${BUNDLE_PATH.replace('/', '.')}" key="group.title"/>""")
    val extension = XmlSerializer.deserialize(element, NotificationGroupEP::class.java)
    val pluginDescriptor = DefaultPluginDescriptor(PluginId.getId("notification.group.lazy.title.test"), classLoader)
    ApplicationManager.getApplication().extensionArea
      .getExtensionPoint<NotificationGroupEP>("com.intellij.notificationGroup")
      .registerExtension(extension, pluginDescriptor, disposable)
  }

  /**
   * Serves the test resource bundle from memory and counts the lookups of its files.
   */
  private class BundleClassLoader(private val bundleContent: String) : ClassLoader(null) {
    val bundleRequests: AtomicInteger = AtomicInteger()

    override fun getResourceAsStream(name: String): InputStream? {
      if (!name.startsWith(BUNDLE_PATH)) {
        return super.getResourceAsStream(name)
      }
      bundleRequests.incrementAndGet()
      return if (name == "$BUNDLE_PATH.properties") bundleContent.byteInputStream() else null
    }
  }
}
