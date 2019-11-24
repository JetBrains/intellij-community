// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.write
import org.junit.Test

class DynamicPluginsTest : LightPlatformTestCase() {
  companion object {
    val receivedNotifications = mutableListOf<UISettings>()
  }

  @Test
  fun testLoadListeners() {
    receivedNotifications.clear()

    MemoryFileSystemBuilder.newEmpty().build(DynamicPluginsTest::class.java.simpleName).use { fs ->
      ApplicationManager.getApplication().messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())

      val pluginFile = fs.getPath("/plugin/META-INF/plugin.xml")
      pluginFile.write("""
        <idea-plugin>
          <name>testLoadListeners</name>
          <applicationListeners>
            <listener class="${MyUISettingsListener::class.java.name}" topic="com.intellij.ide.ui.UISettingsListener"/>
          </applicationListeners>
        </idea-plugin>""")
      val descriptor = loadDescriptorInTest(pluginFile.parent.parent)
      descriptor.setLoader(DynamicPlugins::class.java.classLoader)
      DynamicPlugins.loadPlugin(descriptor, false)
      ApplicationManager.getApplication().messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertEquals(1, receivedNotifications.size)

      DynamicPlugins.unloadPlugin(descriptor, false)
      ApplicationManager.getApplication().messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertEquals(1, receivedNotifications.size)
    }
  }

  private class MyUISettingsListener : UISettingsListener {
    override fun uiSettingsChanged(uiSettings: UISettings) {
      receivedNotifications.add(uiSettings)
    }
  }
}