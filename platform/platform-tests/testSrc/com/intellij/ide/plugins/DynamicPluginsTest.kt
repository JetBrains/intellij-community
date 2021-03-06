// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.ide.plugins

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.ui.switcher.ShowQuickActionPopupAction
import com.intellij.util.KeyedLazyInstanceEP
import com.intellij.util.io.Ksuid
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.Attribute
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

@RunsInEdt
class DynamicPluginsTest {
  companion object {
    val receivedNotifications = mutableListOf<UISettings>()
    val receivedNotifications2 = mutableListOf<UISettings>()

    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  @Rule
  @JvmField
  val runInEdt = EdtRule()

  private fun loadPluginWithText(pluginBuilder: PluginBuilder): Disposable {
    return loadPluginWithText(pluginBuilder, DynamicPluginsTest::class.java.classLoader, inMemoryFs.fs)
  }

  @Test
  fun testLoadListeners() {
    receivedNotifications.clear()

    val app = ApplicationManager.getApplication()
    app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())

    val path = inMemoryFs.fs.getPath("/plugin")
    PluginBuilder().name("testLoadListeners").applicationListeners("""
      <listener class="${MyUISettingsListener::class.java.name}" topic="com.intellij.ide.ui.UISettingsListener"/>
    """.trimIndent()).build(path)
    val descriptor = loadDescriptorInTest(path)
    setPluginClassLoaderForMainAndSubPlugins(descriptor, DynamicPlugins::class.java.classLoader)
    DynamicPlugins.loadPlugin(descriptor)
    app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
    assertThat(receivedNotifications).hasSize(1)

    DynamicPlugins.unloadPlugin(descriptor)
    app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
    assertThat(receivedNotifications).hasSize(1)
  }

  @Test
  fun testClassloaderAfterReload() {
    val path = inMemoryFs.fs.getPath("/plugin")
    val builder = PluginBuilder().randomId("bar").also { it.build(path) }
    val descriptor = loadDescriptorInTest(path)
    assertThat(descriptor).isNotNull

    DynamicPlugins.loadPlugin(descriptor)

    DisabledPluginsState.saveDisabledPlugins(PathManager.getConfigDir(), builder.id)
    DynamicPlugins.unloadPlugin(descriptor, DynamicPlugins.UnloadPluginOptions(disable = true))
    assertThat(PluginManagerCore.getPlugin(descriptor.pluginId)?.pluginClassLoader as? PluginClassLoader).isNull()

    DisabledPluginsState.saveDisabledPlugins(PathManager.getConfigDir())
    val newDescriptor = loadDescriptorInTest(path)
    PluginManagerCore.createClassLoaderConfiguratorForDynamicPlugin(newDescriptor).configure(newDescriptor)
    DynamicPlugins.loadPlugin(newDescriptor)
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
    val service = ApplicationManager.getApplication().getService(MyPersistentComponent::class.java)
    service.myState.stateData = data
    Disposer.dispose(disposable)

    val disposable2 = loadExtensionWithText(extensionTag, DynamicPlugins::class.java.classLoader)
    val service2 = ApplicationManager.getApplication().getService(MyPersistentComponent::class.java)
    assertThat(service2.myState.stateData).isEqualTo(data)
    Disposer.dispose(disposable2)
  }

  @Test
  fun unloadActionReference() {
    val disposable = loadPluginWithText(PluginBuilder().actions("""
          <reference ref="QuickActionPopup">
            <add-to-group group-id="ListActions" anchor="last"/>
          </reference>"""))
    val group = ActionManager.getInstance().getAction("ListActions") as DefaultActionGroup
    assertThat(group.getChildren(null).any { it is ShowQuickActionPopupAction }).isTrue()
    Disposer.dispose(disposable)
    assertThat(group.getChildren(null).any { it is ShowQuickActionPopupAction }).isFalse()
  }

  @Test
  fun unloadGroupWithActions() {
    val disposable = loadPluginWithText(PluginBuilder().actions("""
          <group id="Foo">
            <action id="foo.bar" class="${MyAction::class.java.name}"/>
          </group>"""))
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNotNull()
    Disposer.dispose(disposable)
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNull()
  }

  @Test
  fun unloadGroupWithActionReferences() {
    ActionManager.getInstance()
    val disposable = loadPluginWithText(PluginBuilder().actions("""
          <action id="foo.bar" class="${MyAction::class.java.name}"/>
          <action id="foo.bar2" class="${MyAction2::class.java.name}"/>
          <group id="Foo">
            <reference ref="foo.bar"/>
            <reference ref="foo.bar2"/>
          </group>"""))
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNotNull()
    Disposer.dispose(disposable)
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNull()
  }

  @Test
  fun unloadNestedGroupWithActions() {
    val disposable = loadPluginWithText(PluginBuilder().actions("""
          <group id="Foo">
            <group id="Bar">
              <action id="foo.bar" class="${MyAction::class.java.name}"/>
            </group>  
          </group>"""))
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNotNull()
    Disposer.dispose(disposable)
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNull()
  }

  @Test
  fun loadOptionalDependency() {
    val beforeList = PluginManagerCore.getLoadedPlugins()
    val plugin2Builder = PluginBuilder().randomId("bar")
    val plugin1Disposable = loadPluginWithOptionalDependency(
      PluginBuilder().randomId("foo").packagePrefix("org.foo"),
      PluginBuilder().actions("""<group id="FooBarGroup"></group>""").packagePrefix("org.foo.bar"),
      plugin2Builder
    )
    try {
      val pluginToDisposable = loadPluginWithText(plugin2Builder)
      try {
        assertThat(ActionManager.getInstance().getAction("FooBarGroup")).isNotNull()
      }
      finally {
        Disposer.dispose(pluginToDisposable)
      }
      assertThat(ActionManager.getInstance().getAction("FooBarGroup")).isNull()
    }
    finally {
      Disposer.dispose(plugin1Disposable)
    }

    assertThat(PluginManagerCore.getLoadedPlugins()).isEqualTo(beforeList)
  }

  @Test
  fun loadOptionalDependencyDuplicateNotification() {
    InspectionToolRegistrar.getInstance().createTools()

    val barBuilder = PluginBuilder().randomId("bar")
    val barDisposable = loadPluginWithText(barBuilder)
    val fooDisposable = loadPluginWithOptionalDependency(
      PluginBuilder().extensions("""<globalInspection implementationClass="${MyInspectionTool::class.java.name}"/>"""),
      PluginBuilder().extensions("""<globalInspection implementationClass="${MyInspectionTool2::class.java.name}"/>"""),
      barBuilder
    )
    assertThat(InspectionEP.GLOBAL_INSPECTION.extensions.count {
      it.implementationClass == MyInspectionTool::class.java.name || it.implementationClass == MyInspectionTool2::class.java.name
    }).isEqualTo(2)
    Disposer.dispose(fooDisposable)
    Disposer.dispose(barDisposable)
  }

  @Test
  fun loadOptionalDependencyExtension() {
    val pluginTwoBuilder = PluginBuilder()
      .randomId("optionalDependencyExtension-two")
      .packagePrefix("org.foo.two")
      .extensionPoints("""<extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>""")

    val plugin1Disposable = loadPluginWithOptionalDependency(
      PluginBuilder().randomId("optionalDependencyExtension-one").packagePrefix("org.foo.one"),
      PluginBuilder().extensions("""<barExtension key="foo" implementationClass="y"/>""", "bar").packagePrefix("org.foo"),
      pluginTwoBuilder
    )
    try {
      val plugin2Disposable = loadPluginWithText(pluginTwoBuilder)
      val extensionArea = ApplicationManager.getApplication().extensionArea
      try {
        val ep = extensionArea.getExtensionPointIfRegistered<KeyedLazyInstanceEP<*>>("bar.barExtension")
        assertThat(ep).isNotNull()
        val extensions = ep!!.extensionList
        assertThat(extensions).hasSize(1)
        assertThat(extensions.single().key).isEqualTo("foo")
      }
      finally {
        Disposer.dispose(plugin2Disposable)
      }
      assertThat(extensionArea.hasExtensionPoint("bar.barExtension")).isFalse()
    }
    finally {
      Disposer.dispose(plugin1Disposable)
    }
  }

  @Test
  fun loadOptionalDependencyOwnExtension() {
    val barBuilder = PluginBuilder().randomId("bar").packagePrefix("bar")
    val fooBuilder = PluginBuilder().randomId("foo").packagePrefix("foo")
      .extensionPoints("""<extensionPoint qualifiedName="foo.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>""")
    val plugin1Disposable = loadPluginWithOptionalDependency(
      fooBuilder,
      PluginBuilder().extensions("""<barExtension key="foo" implementationClass="y"/>""", "foo").packagePrefix("foo1"),
      barBuilder
    )
    try {
      val ep = ApplicationManager.getApplication().extensionArea.getExtensionPointIfRegistered<KeyedLazyInstanceEP<*>>("foo.barExtension")
      assertThat(ep).isNotNull()
      val plugin2Disposable = loadPluginWithText(barBuilder)
      try {
        val extension = ep!!.extensionList.single()
        assertThat(extension.key).isEqualTo("foo")
        assertThat(extension.pluginDescriptor).isEqualTo(PluginManagerCore.getPlugin(PluginId.getId(fooBuilder.id)))
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
    val pluginOneBuilder = PluginBuilder().randomId("optionalDependencyDescriptor-one")
    val pluginOneDisposable = loadPluginWithText(pluginOneBuilder)
    val app = ApplicationManager.getApplication()
    try {
      assertThat(app.getService(MyPersistentComponent::class.java)).isNull()
      val pluginTwoId = "optionalDependencyDescriptor-two_${Ksuid.generate()}"
      val pluginTwoDisposable = loadPluginWithOptionalDependency(
        PluginBuilder().id(pluginTwoId),
        PluginBuilder().extensions("""<applicationService serviceImplementation="${MyPersistentComponent::class.java.name}"/>"""),
        pluginOneBuilder
      )
      try {
        assertThat(app.getService(MyPersistentComponent::class.java)).isNotNull()
      }
      finally {
        Disposer.dispose(pluginTwoDisposable)
      }
      assertThat(PluginManagerCore.getPlugin(PluginId.getId(pluginTwoId))).isNull()
      assertThat(app.getService(MyPersistentComponent::class.java)).isNull()
    }
    finally {
      Disposer.dispose(pluginOneDisposable)
    }
  }

  @Test
  fun loadOptionalDependencyListener() {
    receivedNotifications.clear()
    receivedNotifications2.clear()

    val pluginTwoBuilder = PluginBuilder().randomId("optionalDependencyListener-two")
    val pluginOneDisposable = loadPluginWithOptionalDependency(
      PluginBuilder().randomId("optionalDependencyListener-one")
        .applicationListeners("""<listener class="${MyUISettingsListener::class.java.name}" topic="com.intellij.ide.ui.UISettingsListener"/>""")
        .packagePrefix("org.foo.one"),
      PluginBuilder()
        .applicationListeners("""<listener class="${MyUISettingsListener2::class.java.name}" topic="com.intellij.ide.ui.UISettingsListener"/>""")
        .packagePrefix("org.foo"),
      pluginTwoBuilder
    )

    try {
      val app = ApplicationManager.getApplication()
      app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertThat(receivedNotifications).hasSize(1)

      val pluginToDisposable = loadPluginWithText(pluginTwoBuilder)
      try {
        app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
        assertThat(receivedNotifications).hasSize(2)
        assertThat(receivedNotifications2).hasSize(1)
      }
      finally {
        Disposer.dispose(pluginToDisposable)
      }

      app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertThat(receivedNotifications).hasSize(3)
      assertThat(receivedNotifications2).hasSize(1)
    }
    finally {
      Disposer.dispose(pluginOneDisposable)
    }
  }

  @Test
  fun loadOptionalDependencyEP() {
    val pluginTwoBuilder = PluginBuilder().randomId("optionalDependencyListener-two")
    val pluginTwoDisposable = loadPluginWithText(pluginTwoBuilder)
    try {
      val pluginOneDisposable = loadPluginWithOptionalDependency(
        PluginBuilder().randomId("optionalDependencyListener-one"),
        PluginBuilder()
          .extensionPoints("""<extensionPoint qualifiedName="one.foo" interface="java.lang.Runnable" dynamic="true"/>""")
          .extensions("""<foo implementation="${MyRunnable::class.java.name}"/>""", "one"),
        pluginTwoBuilder
      )
      Disposer.dispose(pluginOneDisposable)
    }
    finally {
      Disposer.dispose(pluginTwoDisposable)
    }
  }

  @Test
  fun loadOptionalDependencyEPAdjacentDescriptor() {
    val pluginTwoBuilder = PluginBuilder().randomId("optionalDependencyListener-two")
    val pluginThreeBuilder = PluginBuilder().randomId("optionalDependencyListener-three")
    val pluginTwoDisposable = loadPluginWithText(pluginTwoBuilder)
    val pluginThreeDisposable = loadPluginWithText(pluginThreeBuilder)
    try {
      val pluginDescriptor = PluginBuilder().randomId("optionalDependencyListener-one")
      pluginDescriptor.depends(
        pluginTwoBuilder.id,
        PluginBuilder().extensionPoints("""<extensionPoint qualifiedName="one.foo" interface="java.lang.Runnable" dynamic="true"/>"""))
      pluginDescriptor.depends(
        pluginThreeBuilder.id,
        PluginBuilder().extensions("""<foo implementation="${MyRunnable::class.java.name}"/>""", "one")
      )
      val pluginOneDisposable = loadPluginWithText(pluginDescriptor)
      Disposer.dispose(pluginOneDisposable)
    }
    finally {
      Disposer.dispose(pluginTwoDisposable)
      Disposer.dispose(pluginThreeDisposable)
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
    StartupManagerImpl.addActivityEpListener(project)
    val disposable = loadExtensionWithText("""
      <postStartupActivity implementation="${MyStartupActivity::class.java.name}"/>
      <projectService serviceImplementation="${MyProjectService::class.java.name}"/>
    """.trimIndent(), DynamicPluginsTest::class.java.classLoader)
    try {
      assertThat(project.service<MyProjectService>().executed).isTrue()
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
      assertThat(InspectionEP.GLOBAL_INSPECTION.extensionList.any { it.implementationClass == MyInspectionTool::class.java.name }).isTrue()
    }
    finally {
      Disposer.dispose(disposable)
    }
    assertThat(InspectionEP.GLOBAL_INSPECTION.extensionList.any { it.implementationClass == MyInspectionTool::class.java.name }).isFalse()
  }

  @Test
  fun unloadEPWithTags() {
    val disposable = loadExtensionWithText(
      """
        <intentionAction>
          <bundleName>messages.CommonBundle</bundleName>
          <categoryKey>button.add</categoryKey>
          <className>${MyIntentionAction::class.java.name}</className>
        </intentionAction>""",
      DynamicPlugins::class.java.classLoader)
    try {
      val intention = IntentionManagerImpl.EP_INTENTION_ACTIONS.extensionList.find { it.className == MyIntentionAction::class.java.name }
      assertThat(intention).isNotNull
      intention!!.categories
    }
    finally {
      Disposer.dispose(disposable)
    }
    assertThat(IntentionManagerImpl.EP_INTENTION_ACTIONS.extensionList).allMatch { it.className != MyIntentionAction::class.java.name }
  }

  @Test
  fun unloadEPCollection() {
    val project = projectRule.project
    assertThat(Configurable.PROJECT_CONFIGURABLE.getExtensions(project).any { it.instanceClass == MyConfigurable::class.java.name }).isFalse()
    val listenerDisposable = Disposer.newDisposable()

    val checked = AtomicInteger()
    Configurable.PROJECT_CONFIGURABLE.addChangeListener(project, Runnable {
      checked.incrementAndGet()
    }, listenerDisposable)

    val disposable = loadExtensionWithText("<projectConfigurable instance=\"${MyConfigurable::class.java.name}\" displayName=\"foo\"/>",
                                           DynamicPlugins::class.java.classLoader)
    try {
      assertThat(checked.get()).isEqualTo(1)
      assertThat(
        Configurable.PROJECT_CONFIGURABLE.getExtensions(project).any { it.instanceClass == MyConfigurable::class.java.name }).isTrue()
    }
    finally {
      Disposer.dispose(disposable)
      Disposer.dispose(listenerDisposable)
    }
    assertThat(checked.get()).isEqualTo(2)
    assertThat(
      Configurable.PROJECT_CONFIGURABLE.getExtensions(project).any { it.instanceClass == MyConfigurable::class.java.name }).isFalse()
  }

  @Test
  fun unloadModuleEP() {
    val disposable = loadExtensionWithText(
      """<moduleConfigurationEditorProvider implementation="${MyModuleConfigurationEditorProvider::class.java.name}"/>""")
    Disposer.dispose(disposable)
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
    val pluginBuilder = PluginBuilder()
      .randomId("disableWithoutRestart")
      .extensions("""<applicationService serviceImplementation="${MyPersistentComponent::class.java.name}"/>""")
    val disposable = loadPluginWithText(pluginBuilder)
    val app = ApplicationManager.getApplication()
    assertThat(app.getService(MyPersistentComponent::class.java)).isNotNull()

    val pluginDescriptor = PluginManagerCore.getPlugin(PluginId.getId(pluginBuilder.id))!!

    val disabled = ProjectPluginTrackerManager.instance.updatePluginsState(
      listOf(pluginDescriptor),
      PluginEnableDisableAction.DISABLE_GLOBALLY,
    )
    assertThat(disabled).isTrue()
    assertThat(pluginDescriptor.isEnabled).isFalse()
    assertThat(app.getService(MyPersistentComponent::class.java)).isNull()

    val enabled = ProjectPluginTrackerManager.instance.updatePluginsState(
      listOf(pluginDescriptor),
      PluginEnableDisableAction.ENABLE_GLOBALLY,
    )
    assertThat(enabled).isTrue()
    assertThat(pluginDescriptor.isEnabled).isTrue()
    assertThat(app.getService(MyPersistentComponent::class.java)).isNotNull()

    Disposer.dispose(disposable)
  }

  @Test
  fun canUnloadNestedOptionalDependency() {
    val barBuilder = PluginBuilder().randomId("bar")
      .extensionPoints(
        """<extensionPoint qualifiedName="foo.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP"/>""")
    val quuxBuilder = PluginBuilder().randomId("quux")

    val quuxDependencyDescriptor = PluginBuilder().extensions("""<barExtension key="foo" implementationClass="y"/>""", "foo")
    val barDependencyDescriptor = PluginBuilder().depends(quuxBuilder.id, quuxDependencyDescriptor)
    val mainDescriptor = PluginBuilder().randomId("main").depends(barBuilder.id, barDependencyDescriptor)

    val barDisposable = loadPluginWithText(barBuilder)
    try {
      val quuxDisposable = loadPluginWithText(quuxBuilder)
      try {
        val directory = Files.createTempDirectory(inMemoryFs.fs.getPath("/"), null).resolve("plugin")
        mainDescriptor.build(directory)
        val descriptor = loadDescriptorInTest(directory)
        setPluginClassLoaderForMainAndSubPlugins(descriptor, DynamicPluginsTest::class.java.classLoader)
        assertThat(DynamicPlugins.checkCanUnloadWithoutRestart(descriptor)).isEqualTo(
          "Plugin ${mainDescriptor.id} is not unload-safe because of extension to non-dynamic EP foo.barExtension in optional dependency on ${quuxBuilder.id} in optional dependency on ${barBuilder.id}")
      }
      finally {
        Disposer.dispose(quuxDisposable)
      }
    }
    finally {
      Disposer.dispose(barDisposable)
    }
  }

  @Test
  fun loadNestedOptionalDependency() {
    val barBuilder = PluginBuilder().randomId("bar")
    val quuxBuilder = PluginBuilder().randomId("quux")

    val quuxDependencyDescriptor = PluginBuilder().extensions(
      """<applicationService serviceImplementation="${MyPersistentComponent::class.java.name}"/>""")
    val barDependencyDescriptor = PluginBuilder().depends(quuxBuilder.id, quuxDependencyDescriptor)
    val mainDescriptor = PluginBuilder().depends(barBuilder.id, barDependencyDescriptor)
    val barDisposable = loadPluginWithText(barBuilder)
    try {
      val quuxDisposable = loadPluginWithText(quuxBuilder)
      try {
        val mainDisposable = loadPluginWithText(mainDescriptor)
        try {
          assertThat(ApplicationManager.getApplication().getService(MyPersistentComponent::class.java)).isNotNull()
        }
        finally {
          Disposer.dispose(mainDisposable)
          assertThat(ApplicationManager.getApplication().getService(MyPersistentComponent::class.java)).isNull()
        }
      }
      finally {
        Disposer.dispose(quuxDisposable)
      }
    }
    finally {
      Disposer.dispose(barDisposable)
    }
  }

  @Test
  fun unloadNestedOptionalDependency() {
    val barBuilder = PluginBuilder().randomId("bar")
    val quuxBuilder = PluginBuilder().randomId("quux")

    val quuxDependencyDescriptor = PluginBuilder()
      .extensions("""<applicationService serviceImplementation="${MyPersistentComponent::class.java.name}"/>""")
      .packagePrefix("org.foo.quux")
    val barDependencyDescriptor = PluginBuilder().depends(quuxBuilder.id, quuxDependencyDescriptor)
      .packagePrefix("org.foo.bar")
    val mainDescriptor = PluginBuilder().depends(barBuilder.id, barDependencyDescriptor).packagePrefix("org.foo")

    val barDisposable = loadPluginWithText(barBuilder)
    try {
      val quuxDisposable = loadPluginWithText(quuxBuilder)
      val mainDisposable = loadPluginWithText(mainDescriptor)
      try {
        assertThat(ApplicationManager.getApplication().getService(MyPersistentComponent::class.java)).isNotNull()
        Disposer.dispose(quuxDisposable)
        assertThat(ApplicationManager.getApplication().getService(MyPersistentComponent::class.java)).isNull()
      }
      finally {
        Disposer.dispose(mainDisposable)
      }
    }
    finally {
      Disposer.dispose(barDisposable)
    }
  }

  private fun loadPluginWithOptionalDependency(pluginDescriptor: PluginBuilder,
                                               optionalDependencyDescriptor: PluginBuilder,
                                               dependsOn: PluginBuilder): Disposable {
    pluginDescriptor.depends(dependsOn.id, optionalDependencyDescriptor)
    return loadPluginWithText(pluginDescriptor)
  }
}

private class MyUISettingsListener : UISettingsListener {
  override fun uiSettingsChanged(uiSettings: UISettings) {
    DynamicPluginsTest.receivedNotifications.add(uiSettings)
  }
}

private class MyUISettingsListener2 : UISettingsListener {
  override fun uiSettingsChanged(uiSettings: UISettings) {
    DynamicPluginsTest.receivedNotifications2.add(uiSettings)
  }
}

private data class MyPersistentState(@Attribute var stateData: String? = "")

@State(name = "MyTestState", storages = [Storage("other.xml")], allowLoadInTests = true)
private class MyPersistentComponent : PersistentStateComponent<MyPersistentState> {
  var myState = MyPersistentState("")

  override fun getState() = myState

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
private class MyInspectionTool2 : GlobalInspectionTool()

private class MyConfigurable : Configurable {
  override fun isModified(): Boolean = TODO()
  override fun getDisplayName(): String = TODO()
  override fun apply() {}
  override fun createComponent() = TODO()
}

private class MyAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
  }
}

private class MyAction2 : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
  }
}

private class MyIntentionAction : IntentionAction {
  override fun startInWriteAction() = false
  override fun getFamilyName() = "foo"
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = false
  override fun getText(): String = "foo"
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
  }
}

private class MyRunnable : Runnable {
  override fun run() {
  }
}

private class MyModuleConfigurationEditorProvider : ModuleConfigurationEditorProvider {
  override fun createEditors(state: ModuleConfigurationState?): Array<ModuleConfigurationEditor> {
    return arrayOf()
  }
}
