// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.ui.switcher.ShowQuickActionPopupAction
import com.intellij.util.KeyedLazyInstanceEP
import com.intellij.util.io.write
import com.intellij.util.xmlb.annotations.Attribute
import junit.framework.Assert.*
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class DynamicPluginsTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    @JvmField
    @ClassRule
    val projectRule = ProjectRule()

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

  @Test
  fun testSaveSettingsOnPluginUnload() {
    val data = System.currentTimeMillis().toString()

    val extensionTag = "<applicationService serviceImplementation=\"${MyPersistentComponent::class.java.name}\"/>"
    val disposable = loadExtensionWithText(extensionTag, DynamicPlugins::class.java.classLoader)
    val service = ServiceManager.getService(MyPersistentComponent::class.java)
    service.myState.stateData = data
    Disposer.dispose(disposable)

    val disposable2 = loadExtensionWithText(extensionTag, DynamicPlugins::class.java.classLoader)
    val service2 = ServiceManager.getService(MyPersistentComponent::class.java)
    assertEquals(data, service2.myState.stateData)
    Disposer.dispose(disposable2)
  }

  @Test
  fun unloadActionReference() {
    val disposable = loadPluginWithText("""
      <idea-plugin>
        <id>foo</id>
        <actions>
          <reference ref="QuickActionPopup">
            <add-to-group group-id="ListActions" anchor="last"/>
          </reference>  
        </actions>
      </idea-plugin>
    """.trimIndent(), DynamicPlugins::class.java.classLoader)
    val group = ActionManager.getInstance().getAction("ListActions") as DefaultActionGroup
    assertTrue(group.getChildren(null).any { it is ShowQuickActionPopupAction })
    Disposer.dispose(disposable)
    assertFalse(group.getChildren(null).any { it is ShowQuickActionPopupAction })
  }

  @Test
  fun loadOptionalDependency() {
    val plugin1Disposable = loadPluginWithOptionalDependency(
      """
            <idea-plugin>
                <id>foo</id>
                <depends optional="true" config-file="bar.xml">bar</depends>
            </idea-plugin>
          """,
          """
                    <idea-plugin>
                        <actions>
                            <group id="FooBarGroup">
                            </group>
                        </actions>     
                    </idea-plugin>
                """)
    try {
      val plugin2Disposable = loadPluginWithText("<idea-plugin><id>bar</id></idea-plugin>", DynamicPluginsTest::class.java.classLoader)
      try {
        assertNotNull(ActionManager.getInstance().getAction("FooBarGroup"))
      }
      finally {
        Disposer.dispose(plugin2Disposable)
      }
      assertNull(ActionManager.getInstance().getAction("FooBarGroup"))
    }
    finally {
      Disposer.dispose(plugin1Disposable)
    }
  }

  @Test
  fun loadOptionalDependencyExtension() {
    val plugin1Disposable = loadPluginWithOptionalDependency(
      """
            <idea-plugin>
                <id>foo</id>
                <depends optional="true" config-file="bar.xml">bar</depends>
            </idea-plugin>
          """,
      """
                    <idea-plugin>
                       <extensions defaultExtensionNs="bar">
                         <barExtension key="foo" implementationClass="y"/>
                       </extensions>
                    </idea-plugin>
                """)
    try {
      val plugin2Disposable = loadPluginWithText("""
        <idea-plugin>
          <id>bar</id>
          <extensionPoints>
            <extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>
          </extensionPoints>
        </idea-plugin>
      """.trimIndent(), DynamicPluginsTest::class.java.classLoader)
      try {
        val ep = Extensions.getRootArea().getExtensionPointIfRegistered<KeyedLazyInstanceEP<*>>("bar.barExtension")
        assertNotNull(ep)
        assertEquals("foo", ep!!.extensionList.single().key)
      }
      finally {
        Disposer.dispose(plugin2Disposable)
      }
      assertNull(Extensions.getRootArea().getExtensionPointIfRegistered<KeyedLazyInstanceEP<*>>("bar.barExtension"))
    }
    finally {
      Disposer.dispose(plugin1Disposable)
    }
  }

  @Test
  fun loadOptionalDependencyOwnExtension() {
    val plugin1Disposable = loadPluginWithOptionalDependency(
      """
            <idea-plugin>
                <id>foo</id>
                <extensionPoints>
                  <extensionPoint qualifiedName="foo.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>
                </extensionPoints>
                <depends optional="true" config-file="bar.xml">bar</depends>
            </idea-plugin>
          """,
      """
                    <idea-plugin>
                       <extensions defaultExtensionNs="foo">
                         <barExtension key="foo" implementationClass="y"/>
                       </extensions>
                    </idea-plugin>
                """)
    try {
      val ep = Extensions.getRootArea().getExtensionPointIfRegistered<KeyedLazyInstanceEP<*>>("foo.barExtension")
      assertNotNull(ep)
      val plugin2Disposable = loadPluginWithText("<idea-plugin><id>bar</id></idea-plugin>", DynamicPluginsTest::class.java.classLoader)
      try {
        val extension = ep!!.extensionList.single()
        assertEquals("foo", extension.key)
        assertEquals(PluginManagerCore.getPlugin(PluginId.getId("foo")), extension.pluginDescriptor)
      }
      finally {
        Disposer.dispose(plugin2Disposable)
      }
      assertEquals(0, ep!!.extensionList.size)
    }
    finally {
      Disposer.dispose(plugin1Disposable)
    }
  }


  @Test
  fun testProjectService() {
    val project = projectRule.project
    val disposable = loadExtensionWithText("""
      <projectService serviceImplementation="${MyProjectService::class.java.name}"/>
    """.trimIndent(), DynamicPluginsTest::class.java.classLoader)
    try {
      assertNotNull(ServiceManager.getService(project, MyProjectService::class.java))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun testExtensionOnServiceDependency() {
    val project = projectRule.project
    val disposable = loadExtensionWithText("""
      <postStartupActivity implementation="${MyStartupActivity::class.java.name}"/>
      <projectService serviceImplementation="${MyProjectService::class.java.name}"/>
    """.trimIndent(), DynamicPluginsTest::class.java.classLoader)
    try {
      assertTrue(ServiceManager.getService(project, MyProjectService::class.java).executed)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun loadPluginWithOptionalDependency(pluginXmlText: String, optionalDependencyDescriptorText: String): Disposable {
    val directory = FileUtil.createTempDirectory("test", "test", true)
    val plugin = File(directory, "/plugin/META-INF/plugin.xml")
    FileUtil.createParentDirs(plugin)
    FileUtil.writeToFile(plugin, pluginXmlText.trimIndent())
    FileUtil.writeToFile(File(directory, "/plugin/META-INF/bar.xml"), optionalDependencyDescriptorText.trimIndent())

    val descriptor = loadDescriptorInTest(plugin.toPath().parent.parent)
    descriptor.setLoader(DynamicPluginsTest::class.java.classLoader)
    DynamicPlugins.loadPlugin(descriptor, false)
    return Disposable { DynamicPlugins.unloadPlugin(loadDescriptorInTest(plugin.toPath().parent.parent), false) }
  }

  private class MyUISettingsListener : UISettingsListener {
    override fun uiSettingsChanged(uiSettings: UISettings) {
      receivedNotifications.add(uiSettings)
    }
  }

  private data class MyPersistentState(@Attribute var stateData: String? = "")

  @State(name = "MyTestState", storages = [Storage("other.xml")], allowLoadInTests = true)
  private class MyPersistentComponent : PersistentStateComponent<MyPersistentState> {
    var myState = MyPersistentState("")

    override fun getState(): MyPersistentState? {
      return myState
    }

    override fun loadState(state: MyPersistentState) {
      myState = state
    }
  }

  private class MyStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
      val service = project.getService(MyProjectService::class.java)
      if (service != null) {
        service.executed = true
      }
    }
  }

  private class MyProjectService {
    companion object {
      val LOG = Logger.getInstance(MyProjectService::class.java)
    }

    init {
      LOG.info("MyProjectService initialized")
    }

    var executed = false
  }
}