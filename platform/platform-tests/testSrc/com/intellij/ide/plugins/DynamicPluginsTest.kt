// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.ide.plugins

import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionEP
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
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.ui.switcher.ShowQuickActionPopupAction
import com.intellij.util.KeyedLazyInstanceEP
import com.intellij.util.io.write
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.Attribute
import org.junit.*
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
    assertThat(receivedNotifications).hasSize(1)

    DynamicPlugins.unloadPlugin(descriptor, false)
    ApplicationManager.getApplication().messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
    assertThat(receivedNotifications).hasSize(1)
  }

  @Test
  fun testClassloaderAfterReload() {
    val pluginFile = inMemoryFs.fs.getPath("/plugin/META-INF/plugin.xml")
    pluginFile.write("<idea-plugin>\n  <id>bar</id>\n</idea-plugin>")
    val descriptor = loadDescriptorInTest(pluginFile.parent.parent)
    assertThat(descriptor).isNotNull

    DynamicPlugins.loadPlugin(descriptor, false)

    PluginManagerCore.saveDisabledPlugins(arrayListOf(PluginId.getId("bar")), false)
    DynamicPlugins.unloadPlugin(descriptor, true)
    assertThat(PluginManagerCore.getPlugin(descriptor.pluginId)?.pluginClassLoader as? PluginClassLoader).isNull()

    PluginManagerCore.saveDisabledPlugins(arrayListOf(), false)
    val newDescriptor = loadDescriptorInTest(pluginFile.parent.parent)
    PluginManagerCore.initClassLoader(newDescriptor)
    DynamicPlugins.loadPlugin(newDescriptor, true)
    try {
      assertThat(PluginManagerCore.getPlugin(descriptor.pluginId)?.pluginClassLoader as? PluginClassLoader).isNotNull()
    }
    finally {
      DynamicPlugins.unloadPlugin(newDescriptor)
    }
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
    assertThat(service2.myState.stateData).isEqualTo(data)
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
    assertThat(group.getChildren(null).any { it is ShowQuickActionPopupAction }).isTrue()
    Disposer.dispose(disposable)
    assertThat(group.getChildren(null).any { it is ShowQuickActionPopupAction }).isFalse()
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
        assertThat(ActionManager.getInstance().getAction("FooBarGroup")).isNotNull()
      }
      finally {
        Disposer.dispose(plugin2Disposable)
      }
      assertThat(ActionManager.getInstance().getAction("FooBarGroup")).isNull()
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
        assertThat(ep).isNotNull()
        assertThat(ep!!.extensionList.single().key).isEqualTo("foo")
      }
      finally {
        Disposer.dispose(plugin2Disposable)
      }
      assertThat(Extensions.getRootArea().getExtensionPointIfRegistered<KeyedLazyInstanceEP<*>>("bar.barExtension")).isNull()
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
      assertThat(ep).isNotNull()
      val plugin2Disposable = loadPluginWithText("<idea-plugin><id>bar</id></idea-plugin>", DynamicPluginsTest::class.java.classLoader)
      try {
        val extension = ep!!.extensionList.single()
        assertThat(extension.key).isEqualTo("foo")
        assertThat(extension.pluginDescriptor).isEqualTo(PluginManagerCore.getPlugin(PluginId.getId("foo")))
      }
      finally {
        Disposer.dispose(plugin2Disposable)
      }
      assertThat(ep!!.extensionList).isEmpty()
    }
    finally {
      Disposer.dispose(plugin1Disposable)
    }
  }

  @Test
  fun loadOptionalDependencyDescriptor() {
    val plugin1Disposable = loadPluginWithText("<idea-plugin><id>foo</id></idea-plugin>", DynamicPluginsTest::class.java.classLoader)
    try {
      assertThat(ServiceManager.getService(MyPersistentComponent::class.java)).isNull()
      val plugin2Disposable = loadPluginWithOptionalDependency(
        """<idea-plugin>
                <id>bar</id>
                <depends optional="true" config-file="bar.xml">foo</depends>
            </idea-plugin>""",
        """<idea-plugin>
           <extensions defaultExtensionNs="com.intellij">
             <applicationService serviceImplementation="${MyPersistentComponent::class.java.name}"/>
           </extensions>  
         </idea-plugin>
       """
      )
      try {
        assertThat(ServiceManager.getService(MyPersistentComponent::class.java)).isNotNull()
      }
      finally {
        Disposer.dispose(plugin2Disposable)
      }
      assertThat(ServiceManager.getService(MyPersistentComponent::class.java)).isNull()
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
      assertThat(ServiceManager.getService(project, MyProjectService::class.java)).isNotNull()
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
      assertThat(ServiceManager.getService(project, MyProjectService::class.java).executed).isTrue()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun unloadEPWithDefaultAttributes() {
    val disposable = loadExtensionWithText(
      "<globalInspection implementationClass=\"${MyInspectionTool::class.java.name}\" cleanupTool=\"false\"/>",
      DynamicPlugins::class.java.classLoader)
    try {
      assertThat(InspectionEP.GLOBAL_INSPECTION.extensions.any { it.implementationClass == MyInspectionTool::class.java.name }).isTrue()
    }
    finally {
      Disposer.dispose(disposable)
    }
    assertThat(InspectionEP.GLOBAL_INSPECTION.extensions.any { it.implementationClass == MyInspectionTool::class.java.name }).isFalse()
  }

  @Test
  fun unloadEPCollection() {
    val project = projectRule.project
    val disposable = loadExtensionWithText("<projectConfigurable instance=\"${MyConfigurable::class.java.name}\" displayName=\"foo\"/>",
                                           DynamicPlugins::class.java.classLoader)
    try {
      assertThat(Configurable.PROJECT_CONFIGURABLE.getExtensions(project).any { it.instanceClass == MyConfigurable::class.java.name }).isTrue()
    }
    finally {
      Disposer.dispose(disposable)
    }
    assertThat(Configurable.PROJECT_CONFIGURABLE.getExtensions(project).any { it.instanceClass == MyConfigurable::class.java.name }).isFalse()
  }

  @Test
  fun loadExistingFileTypeModification() {
    @Suppress("SpellCheckingInspection")
    val textToLoad = "<fileType name=\"PLAIN_TEXT\" language=\"PLAIN_TEXT\" fileNames=\".texttest\"/>"
    var disposable = loadExtensionWithText(textToLoad, DynamicPlugins::class.java.classLoader)
    Disposer.dispose(disposable)

    UIUtil.dispatchAllInvocationEvents()
    disposable = loadExtensionWithText(textToLoad, DynamicPlugins::class.java.classLoader)
    Disposer.dispose(disposable)
  }

  @Test
  fun disableWithoutRestart() {
    val pluginId = "disableWithoutRestart" + System.currentTimeMillis()
    val disposable = loadPluginWithText("""
      <idea-plugin>
         <id>$pluginId</id>
         <extensions defaultExtensionNs="com.intellij">
           <applicationService serviceImplementation="${MyPersistentComponent::class.java.name}"/>
         </extensions>
      </idea-plugin>""".trimIndent(), DynamicPlugins::class.java.classLoader)
    assertThat(ServiceManager.getService(MyPersistentComponent::class.java)).isNotNull()

    val pluginDescriptor = PluginManagerCore.getPlugin(PluginId.getId(pluginId))!!
    val success = PluginEnabler.updatePluginEnabledState(emptyList(), listOf(pluginDescriptor), null)
    assertThat(success).isTrue()
    assertThat(pluginDescriptor.isEnabled).isFalse()
    assertThat(ServiceManager.getService(MyPersistentComponent::class.java)).isNull()

    assertThat(PluginEnabler.updatePluginEnabledState(listOf(pluginDescriptor), emptyList(), null)).isTrue()
    assertThat(pluginDescriptor.isEnabled).isTrue()
    assertThat(ServiceManager.getService(MyPersistentComponent::class.java)).isNotNull()

    Disposer.dispose(disposable)
  }

  private fun loadPluginWithOptionalDependency(pluginXmlText: String, optionalDependencyDescriptorText: String): Disposable {
    val directory = FileUtil.createTempDirectory("test", "test", true)
    val plugin = File(directory, "/plugin/META-INF/plugin.xml")
    FileUtil.createParentDirs(plugin)
    FileUtil.writeToFile(plugin, pluginXmlText.trimIndent())
    FileUtil.writeToFile(File(directory, "/plugin/META-INF/bar.xml"), optionalDependencyDescriptorText.trimIndent())

    val descriptor = loadDescriptorInTest(plugin.toPath().parent.parent)
    descriptor.setLoader(DynamicPluginsTest::class.java.classLoader)
    assertThat(DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor)).isTrue()

    DynamicPlugins.loadPlugin(descriptor, false)

    return Disposable {
      val unloadDescriptor = loadDescriptorInTest(plugin.toPath().parent.parent)
      val canBeUnloaded = DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor)
      DynamicPlugins.unloadPlugin(unloadDescriptor, false)
      assertThat(canBeUnloaded).isTrue()
    }
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

  private class MyInspectionTool : GlobalInspectionTool()

  private class MyConfigurable : Configurable {
    override fun isModified(): Boolean = TODO()
    override fun getDisplayName(): String = TODO()
    override fun apply() {}
    override fun createComponent() = TODO()
  }
}