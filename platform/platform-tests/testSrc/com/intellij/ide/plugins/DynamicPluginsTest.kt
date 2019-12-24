// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.write
import junit.framework.Assert.*
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DynamicPluginsTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    val receivedNotifications = mutableListOf<UISettings>()
  }
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  @Rule
  @JvmField
  val runInEdt = EdtRule()

  @Test
  fun testLoadListeners() {
    receivedNotifications.clear()

    ApplicationManager.getApplication().messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())

    val pluginFile = inMemoryFs.fs.getPath("/plugin/META-INF/plugin.xml")
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

  @Test
  fun testClassloaderAfterReload() {
    val pluginFile = inMemoryFs.fs.getPath("/plugin/META-INF/plugin.xml")
    pluginFile.write("<idea-plugin>\n  <id>bar</id>\n</idea-plugin>")
    val descriptor = loadDescriptorInTest(pluginFile.parent.parent)
    Assertions.assertThat(descriptor).isNotNull

    DynamicPlugins.loadPlugin(descriptor, false)

    PluginManagerCore.saveDisabledPlugins(arrayListOf(PluginId.getId("bar")), false)
    DynamicPlugins.unloadPlugin(descriptor, true)
    assertNull(PluginManagerCore.getPlugin(descriptor.pluginId)?.pluginClassLoader as? PluginClassLoader)

    PluginManagerCore.saveDisabledPlugins(arrayListOf(), false)
    val newDescriptor = loadDescriptorInTest(pluginFile.parent.parent)
    PluginManagerCore.initClassLoader(newDescriptor)
    DynamicPlugins.loadPlugin(newDescriptor, true)
    assertNotNull(PluginManagerCore.getPlugin(descriptor.pluginId)?.pluginClassLoader as? PluginClassLoader)
  }

  private class MyUISettingsListener : UISettingsListener {
    override fun uiSettingsChanged(uiSettings: UISettings) {
      receivedNotifications.add(uiSettings)
    }
  }
}