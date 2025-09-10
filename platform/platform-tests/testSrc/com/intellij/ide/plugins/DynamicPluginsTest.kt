// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.ide.plugins

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.ide.actions.ContextHelpAction
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.testPluginSrc.bar.BarAction
import com.intellij.ide.plugins.testPluginSrc.bar.BarService
import com.intellij.ide.plugins.testPluginSrc.foo.FooAction
import com.intellij.ide.plugins.testPluginSrc.foo.bar.FooBarAction
import com.intellij.ide.plugins.testPluginSrc.foo.bar.FooBarService
import com.intellij.ide.plugins.testPluginSrc.foo.ep.FooExtension
import com.intellij.ide.plugins.testPluginSrc.foo.ep.FooExtensionService
import com.intellij.ide.plugins.testPluginSrc.foo.epImpl.FooExtensionImpl
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.idea.TestFor
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.loadDescriptorInTest
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.platform.testFramework.plugins.*
import com.intellij.platform.testFramework.setPluginClassLoaderForMainAndSubPlugins
import com.intellij.platform.testFramework.unloadAndUninstallPlugin
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.ui.switcher.ShowQuickActionPopupAction
import com.intellij.util.KeyedLazyInstanceEP
import com.intellij.util.application
import com.intellij.util.io.directoryContent
import com.intellij.util.io.java.classFile
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.Attribute
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@Suppress("UnresolvedPluginConfigReference")
@RunsInEdt
class DynamicPluginsTest {
  companion object {
    val receivedNotifications = mutableListOf<UISettings>()
    val receivedNotifications2 = mutableListOf<UISettings>()
  }

  // per test
  @Rule
  @JvmField
  val projectRule = ProjectRule()

  // FIXME in-memory fs does not work, NonShareableJavaZipFilePool wants .toFile()
  //@Rule
  //@JvmField
  //val inMemoryFs = InMemoryFsRule()
  // private val rootPath get() = inMemoryFs.fs.getPath("/")

  @Rule
  @JvmField
  val tempDir: TempDirectory = TempDirectory()

  private val rootPath get() = tempDir.rootPath
  private val pluginsDir get() = rootPath.resolve("plugins")

  @Rule
  @JvmField
  val runInEdt = EdtRule()

  private fun loadPluginWithText(
    pluginSpec: PluginSpec,
  ): Disposable {
    return com.intellij.platform.testFramework.loadPluginWithText(
      pluginSpec = pluginSpec,
      pluginsDir = pluginsDir,
    )
  }

  @Test
  fun testLoadListeners() {
    receivedNotifications.clear()

    val app = ApplicationManager.getApplication()
    app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())

    val plugin = plugin("testLoadListeners") {
      dependsIntellijModulesLang()
      applicationListeners = """<listener class="${MyUISettingsListener::class.java.name}" topic="com.intellij.ide.ui.UISettingsListener"/>"""
    }
    val descriptor = loadDescriptorInTest(plugin, pluginsDir)

    setPluginClassLoaderForMainAndSubPlugins(descriptor, DynamicPlugins::class.java.classLoader)
    DynamicPlugins.loadPlugin(descriptor)
    app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
    assertThat(receivedNotifications).hasSize(1)

    unloadAndUninstallPlugin(descriptor)
    app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
    assertThat(receivedNotifications).hasSize(1)
  }

  @Test
  fun testClassloaderAfterReload() {
    val bar = plugin("bar") { dependsIntellijModulesLang() }
    val barPath = bar.buildDistribution(pluginsDir)
    val descriptor = loadDescriptorInTest(fileOrDir = barPath)
    assertThat(descriptor).isNotNull

    DynamicPlugins.loadPlugin(descriptor)
    try {
      DisabledPluginsState.saveDisabledPluginsAndInvalidate(PathManager.getConfigDir(), listOf(bar.id!!))
    }
    finally {
      unloadAndUninstallPlugin(descriptor)
    }
    assertThat(PluginManagerCore.getPlugin(descriptor.pluginId)?.pluginClassLoader as? PluginClassLoader).isNull()

    DisabledPluginsState.saveDisabledPluginsAndInvalidate(PathManager.getConfigDir())
    val newDescriptor = loadDescriptorInTest(barPath)
    ClassLoaderConfigurator(PluginManagerCore.getPluginSet()
                              .withPlugin(newDescriptor)
                              .createPluginSetWithEnabledModulesMap())
      .configureModule(newDescriptor)
    DynamicPlugins.loadPlugin(newDescriptor)
    try {
      assertThat(PluginManagerCore.getPlugin(descriptor.pluginId)?.pluginClassLoader as? PluginClassLoader).isNotNull()
    }
    finally {
      unloadAndUninstallPlugin(newDescriptor)
    }
  }

  @Test
  fun testSaveSettingsOnPluginUnload() {
    val data = System.currentTimeMillis().toString()

    val extensionTag = """<applicationService serviceInterface="${MyPersistentComponent::class.java.name}" 
      |serviceImplementation="${MyPersistentComponentImpl::class.java.name}"/>""".trimMargin()

    loadExtensionWithText(extensionTag).use {
      val service = ApplicationManager.getApplication()
        .getService(MyPersistentComponent::class.java)
      assertThat(service).isInstanceOf(MyPersistentComponentImpl::class.java)

      (service as MyPersistentComponentImpl).state.stateData = data
    }

    loadExtensionWithText(extensionTag).use {
      val service = ApplicationManager.getApplication()
        .getService(MyPersistentComponent::class.java)
      assertThat(service).isNotNull

      assertThat(service.data).isEqualTo(data)
    }
  }

  @Test
  fun unloadActionReference() {
    val disposable = loadPluginWithText(plugin {
      dependsIntellijModulesLang()
      actions = """
          <reference ref="QuickActionPopup">
            <add-to-group group-id="ListActions" anchor="last"/>
          </reference>"""
    })
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("ListActions") as DefaultActionGroup
    assertThat(group.getChildren(actionManager).any { it is ShowQuickActionPopupAction }).isTrue()
    Disposer.dispose(disposable)
    assertThat(group.getChildren(actionManager).any { it is ShowQuickActionPopupAction }).isFalse()
  }

  @Test
  fun unloadGroupWithActions() {
    val disposable = loadPluginWithText(plugin {
      dependsIntellijModulesLang()
      actions = """
          <group id="Foo">
            <action id="foo.bar" class="${MyAction::class.java.name}"/>
          </group>"""
    })
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNotNull()
    Disposer.dispose(disposable)
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNull()
  }

  @Test
  fun unloadGroupWithActionReferences() {
    ActionManager.getInstance()
    val disposable = loadPluginWithText(plugin {
      dependsIntellijModulesLang()
      actions = """
        <action id="foo.bar" class="${MyAction::class.java.name}"/>
        <action id="foo.bar2" class="${MyAction2::class.java.name}"/>
        <group id="Foo">
          <reference ref="foo.bar"/>
          <reference ref="foo.bar2"/>
        </group>"""
    })
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNotNull()
    Disposer.dispose(disposable)
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNull()
  }

  @Test
  fun unloadNestedGroupWithActions() {
    val disposable = loadPluginWithText(plugin {
      dependsIntellijModulesLang()
      actions = """
          <group id="Foo">
            <group id="Bar">
              <action id="foo.bar" class="${MyAction::class.java.name}"/>
            </group>  
          </group>"""
    })
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNotNull()
    Disposer.dispose(disposable)
    assertThat(ActionManager.getInstance().getAction("foo.bar")).isNull()
  }

  @Test
  fun unloadActionOverride() {
    val disposable = loadPluginWithText(plugin {
      dependsIntellijModulesLang()
      actions = """<action id="ContextHelp" class="${MyAction::class.java.name}" overrides="true"/>"""
    })
    assertThat(ActionManager.getInstance().getAction("ContextHelp")).isInstanceOf(MyAction::class.java)
    Disposer.dispose(disposable)
    assertThat(ActionManager.getInstance().getAction("ContextHelp")).isInstanceOf(ContextHelpAction::class.java)
  }

  @Test
  fun loadNonDynamicEP() {
    val epName = "one.foo"
    val plugin = plugin("nonDynamic") {
      dependsIntellijModulesLang()
      extensionPoints = """<extensionPoint qualifiedName="$epName" interface="java.lang.Runnable"/>"""
      extensions("""<foo implementation="${MyRunnable::class.java.name}"/>""", "one")
    }
    val descriptor = loadDescriptorInTest(plugin, pluginsDir)
    assertThat(DynamicPlugins.checkCanUnloadWithoutRestart(descriptor))
      .isEqualTo("Plugin ${descriptor.pluginId} is not unload-safe because of extension to non-dynamic EP $epName")
  }

  @Test
  fun loadOptionalDependency() {
    // match production - on plugin load/unload ActionManager is already initialized
    val actionManager = ActionManager.getInstance()

    runAndCheckThatNoNewPlugins {
      val dependency = plugin("dependency") {
        packagePrefix = "org.dependency"
        dependsIntellijModulesLang()
      }
      val dependent = plugin("dependent") {
        packagePrefix = "org.dependent"
        dependsIntellijModulesLang()
        content {
          module("org.dependent") {
            packagePrefix = "org.dependent.sub"
            dependencies {
              plugin("dependency")
            }
            actions = """<group id="FooBarGroup"/>"""
          }
        }
      }
      loadPluginWithText(dependent).use {
        assertThat(actionManager.getAction("FooBarGroup")).isNull()

        runAndCheckThatNoNewPlugins {
          loadPluginWithText(dependency).use {
            assertThat(actionManager.getAction("FooBarGroup")).isNotNull()
          }
        }
        assertThat(actionManager.getAction("FooBarGroup")).isNull()
      }
    }
  }

  @Test
  fun loadOptionalDependencyDuplicateNotification() {
    InspectionToolRegistrar.getInstance().createTools()

    val barBuilder = plugin("bar") { dependsIntellijModulesLang() }
    val barDisposable = loadPluginWithText(barBuilder)
    val fooDisposable = loadPluginWithText(plugin {
      dependsIntellijModulesLang()
      depends("bar", "bar.xml") {
        dependsIntellijModulesLang()
        extensions("""<globalInspection implementationClass="${MyInspectionTool2::class.java.name}"/>""")
      }
      extensions("""<globalInspection implementationClass="${MyInspectionTool::class.java.name}"/>""")
    })
    assertThat(InspectionEP.GLOBAL_INSPECTION.extensions.count {
      it.implementationClass == MyInspectionTool::class.java.name || it.implementationClass == MyInspectionTool2::class.java.name
    }).isEqualTo(2)
    Disposer.dispose(fooDisposable)
    Disposer.dispose(barDisposable)
  }

  @Test
  fun loadOptionalDependencyExtension() {
    val pluginTwoBuilder = plugin("optionalDependencyExtension-two") {
      packagePrefix = "org.foo.two"
      dependsIntellijModulesLang()
      extensionPoints = """<extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"""
    }
    val plugin1Disposable = loadPluginWithText(plugin("optionalDependencyExtension-one") {
      packagePrefix = "org.foo.one"
      content {
        module("intellij.foo.one.module1") {
          packagePrefix = "org.foo"
          dependencies { module(pluginTwoBuilder.id!!) } // TODO this shouldn't work o_O
          extensions("""<barExtension key="foo" implementationClass="y"/>""", "bar")
        }
      }
    })
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
  fun loadModuleClassloader() {
    val fooJar = "foo.jar"
    val barJar = "bar.jar"

    val pluginsPath = directoryContent {
      zip(fooJar) {
        dir("META-INF") {
          file(
            name = "plugin.xml",
            text = """<idea-plugin package="foo">
                     |  <id>foo</id>
                     |  <content>
                     |    <module name="foo.bar"/>
                     |  </content>
                     |</idea-plugin>""".trimIndent(),
          )
        }
        file(
          name = "foo.bar.xml",
          text = """<idea-plugin package="foo.bar">
                   |  <dependencies>
                   |    <plugin id="bar"/>
                   |  </dependencies>
                   |</idea-plugin>""".trimIndent(),
        )
        classFile("foo.Foo") {}
        classFile("foo.bar.BarImpl") {}
      }
      zip(barJar) {
        dir("META-INF") {
          file(
            name = "plugin.xml",
            text = """<idea-plugin> <!-- no package prefix -->
                     |  <id>bar</id>
                     |  <content>
                     |    <module name="bar.foo"/>
                     |  </content>
                     |</idea-plugin>""".trimIndent(),
          )
        }
        file(
          name = "bar.foo.xml",
          text = """<idea-plugin package="bar.foo">
                   |  <dependencies>
                   |    <plugin id="foo"/>
                   |  </dependencies>
                   |</idea-plugin>""".trimIndent(),
        )
        classFile("bar.Bar") {}
        classFile("bar.foo.FooImpl") {}
      }
    }.generateInTempDir()

    fun forNameInModuleClassloader(className: String, moduleName: String): Class<*>? {
      return findEnabledModuleByName(moduleName)?.classLoader?.let {
        Class.forName(className, true, it)
      }
    }

    val barDescriptor = loadDescriptorInTest(pluginsPath.resolve(barJar))
    try {
      assertThat(DynamicPlugins.loadPlugin(barDescriptor)).isTrue()

      val fooDescriptor = loadDescriptorInTest(pluginsPath.resolve(fooJar))
      try {
        assertThat(DynamicPlugins.loadPlugin(fooDescriptor)).isTrue()

        assertThat(forNameInModuleClassloader("bar.foo.FooImpl", "bar.foo")).isNotNull
        assertThat(forNameInModuleClassloader("foo.bar.BarImpl", "foo.bar")).isNotNull
      }
      finally {
        unloadAndUninstallPlugin(fooDescriptor)
      }
    }
    finally {
      unloadAndUninstallPlugin(barDescriptor)
    }
  }

  @Test
  fun `optional plugin dependency loading`() {
    val fooJar = pluginsDir.resolve("foo.jar")
    val barJar = pluginsDir.resolve("bar.jar")
    plugin("foo") {
      depends("bar", "bar.xml") {
        appService<FooBarService>()
      }
      includePackageClassFiles<FooBarService>()
    }.buildMainJar(fooJar)
    plugin("bar") {
      appService<BarService>()
      includePackageClassFiles<BarService>()
    }.buildMainJar(barJar)

    val fooDescriptor = loadDescriptorInTest(fooJar)
    try {
      assertThat(DynamicPlugins.loadPlugin(fooDescriptor)).isTrue()
      val barDescriptor = loadDescriptorInTest(barJar)
      try {
        // FIXME perhaps it should return false to indicate that restart is needed to load more stuff
        assertThat(DynamicPlugins.loadPlugin(barDescriptor)).isTrue()
        val barService = application.getService(barDescriptor.loadClassInsideSelf<BarService>()) as PluginTestHandle
        barService.test()
        val fooBarClass = fooDescriptor.loadClassInsideSelf<FooBarService>() // loaded because packed into the same jar with the main descriptor
        assertThat(application.getService(fooBarClass)).isNull()
        assertThat(fooDescriptor.dependencies.first().subDescriptor!!.isMarkedForLoading).isFalse
        assertThat(fooDescriptor.dependencies.first().subDescriptor!!.pluginClassLoader).isNull()
      }
      finally {
        unloadAndUninstallPlugin(barDescriptor)
      }
    }
    finally {
      unloadAndUninstallPlugin(fooDescriptor)
    }
  }

  @Test
  fun `separate content module jar unloading`() {
    val fooDir = pluginsDir.resolve("foo")
    val barJar = pluginsDir.resolve("bar.jar")
    plugin("foo") {
      content {
        module("foo.bar") {
          isSeparateJar = true
          dependencies {
            plugin("bar")
          }
          action<FooBarAction>()
          includePackageClassFiles<FooBarAction>()
        }
      }
      action<FooAction>()
      includePackageClassFiles<FooAction>()
    }.buildDir(fooDir)
    plugin("bar") {
      action<BarAction>()
      includePackageClassFiles<BarAction>()
    }.buildMainJar(barJar)

    val bar = loadDescriptorInTest(barJar)
    val foo = loadDescriptorInTest(fooDir)
    try {
      val fooBar = foo.contentModules.first()
      assertThat(DynamicPlugins.loadPlugins(listOf(foo, bar), null)).isTrue
      assertThat(fooBar.pluginClassLoader).isNotNull()
      assertThat(DynamicPlugins.unloadPlugins(listOf(bar), null)).isTrue
      assertThat(fooBar.pluginClassLoader).isNull()
    }
    finally {
      assertThat(DynamicPlugins.unloadPlugins(listOf(foo), null)).isTrue
    }
  }

  @Test
  fun `extension point from an embedded content module used in main descriptor`() {
    val fooPath = pluginsDir.resolve("foo")
    plugin("foo") {
      content {
        module("foo.emb", loadingRule = ModuleLoadingRule.EMBEDDED) {
          isSeparateJar = true
          extensionPoint<FooExtension>(FooExtension.EP_FQN, dynamic = true)
          includePackageClassFiles<FooExtension>()
        }
      }
      extension<FooExtensionImpl>(FooExtension.EP_FQN)
      includePackageClassFiles<FooExtensionImpl>()
    }.buildDir(fooPath)
    val foo = loadDescriptorInTest(fooPath)
    val fooEmb = foo.contentModules.first()
    try {
      assertThat(DynamicPlugins.loadPlugins(listOf(foo), null)).isTrue
      assertThat(application.extensionArea.getExtensionPoint<Any>(FooExtension.EP_FQN).extensions.size).isEqualTo(1)
      val epService = application.getService(fooEmb.loadClassInsideSelf<FooExtensionService>()) as PluginTestHandle
      epService.test()
    }
    finally {
      assertThat(DynamicPlugins.unloadPlugins(listOf(foo), null)).isTrue
    }
  }

  @Test
  fun loadOptionalDependencyOwnExtension() {
    val bar = plugin("bar") {
      packagePrefix = "bar"
      dependsIntellijModulesLang()
    }
    val foo = plugin("foo") {
      packagePrefix = "foo"
      dependsIntellijModulesLang()
      extensionPoints = """<extensionPoint qualifiedName="foo.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"""
      content {
        module("intellij.foo.bar") {
          packagePrefix = "foo.bar"
          extensions("""<barExtension key="foo" implementationClass="y"/>""", "foo")
          dependencies { plugin("bar") }
        }
      }
    }
    loadPluginWithText(foo).use {
      val ep = ApplicationManager.getApplication().extensionArea.getExtensionPointIfRegistered<KeyedLazyInstanceEP<*>>("foo.barExtension")
      assertThat(ep).isNotNull()

      loadPluginWithText(bar).use {
        assertThat(ep!!.extensionList).hasSize(1)

        val extension = ep.extensionList.single()
        assertThat(extension.key).isEqualTo("foo")

        assertThat(extension.pluginDescriptor)
          .isNotNull
          .isEqualTo(findEnabledModuleByName("intellij.foo.bar"))
      }

      assertThat(ep!!.extensionList).isEmpty()
    }
  }

  @Test
  fun testExcessDependency() {
    val foo = plugin("com.intellij.foo") {
      packagePrefix = "com.intellij.foo"
      dependsIntellijModulesLang()
    }
    val bar = plugin("com.intellij.bar") {
      packagePrefix = "com.intellij.bar"
      dependsIntellijModulesLang()
      content {
        module("intellij.bar.foo") {
          packagePrefix = "com.intellij.bar.foo"
          dependencies { plugin(foo.id!!) }
        }
      }
    }
    val baz = plugin("com.intellij.baz") {
      packagePrefix = "com.intellij.baz"
      dependsIntellijModulesLang()
      content {
        module("intellij.baz.foo") {
          packagePrefix = "com.intellij.baz.foo"
          dependencies {
            plugin(foo.id!!)
            plugin(bar.id!!)
            module("intellij.bar.foo")
          }
        }
      }
    }

    fun assertForModules(assertion: (String) -> Unit) {
      listOf(
        "intellij.bar.foo",
        "intellij.baz.foo",
      ).forEach(assertion)
    }

    loadPluginWithText(foo).use {
      loadPluginWithText(baz).use {
        assertForModules(::assertModuleIsNotLoaded)
        loadPluginWithText(bar).use {
          assertForModules(::assertModuleIsLoaded)
        }
        assertForModules(::assertModuleIsNotLoaded)
      }
    }
  }

  @Test
  @TestFor(issues = ["IDEA-287123"])
  fun testModulesConfiguration() {
    val foo = plugin("com.intellij.foo") {
      packagePrefix = "com.intellij.foo"
    }
    val bar = plugin("com.intellij.bar") {
      packagePrefix = "com.intellij.bar"
      content {
        module("intellij.bar.foo") {
          packagePrefix = "com.intellij.bar.foo"
          dependencies { plugin(foo.id!!) }
          extensions("""<multiHostInjector implementation="com.intellij.bar.foo.InjectorImpl"/>""")
        }
      }
    }
    val baz = plugin("com.intellij.baz") {
      packagePrefix = "com.intellij.baz"
      dependsIntellijModulesLang()
      content {
        module("intellij.baz.bar") {
          packagePrefix = "com.intellij.baz.bar"
          dependencies { plugin(bar.id!!) }
        }
        module("intellij.baz.bar.foo") {
          packagePrefix = "com.intellij.baz.bar.foo"
          dependencies {
            plugin(foo.id!!)
            module("intellij.bar.foo")
            module("intellij.baz.bar")
          }
          extensions("""<multiHostInjector implementation="com.intellij.baz.bar.foo.InjectorImpl"/>""")
        }
      }
    }

    fun assertForModules(assertion: (String) -> Unit) {
      listOf(
        "intellij.bar.foo",
        "intellij.baz.bar",
        "intellij.baz.bar.foo",
      ).forEach(assertion)
    }

    val ep = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getPoint(projectRule.project) as ExtensionPointImpl<MultiHostInjector>
    val coreInjectorsCount = ep.size()

    loadPluginWithText(
      pluginSpec = baz,
    ).use {
      assertForModules(::assertModuleIsNotLoaded)
      assertThat(ep.size()).isEqualTo(coreInjectorsCount)

      loadPluginWithText(
        pluginSpec = foo,
      ).use {
        assertForModules(::assertModuleIsNotLoaded)
        assertThat(ep.size()).isEqualTo(coreInjectorsCount)

        loadPluginWithText(pluginSpec = bar).use {
          assertForModules(::assertModuleIsLoaded)
          assertThat(ep.size()).isEqualTo(coreInjectorsCount + 2)
        }
      }
    }
  }

  @Test
  fun loadOptionalDependencyDescriptor() {
    val pluginOne = plugin("optionalDependencyDescriptor-one") { dependsIntellijModulesLang() }
    val app = ApplicationManager.getApplication()
    loadPluginWithText(pluginOne).use {
      assertThat(app.getService(MyPersistentComponent::class.java)).isNull()
      val pluginTwoId = "optionalDependencyDescriptor-two"
      loadPluginWithText(plugin(pluginTwoId) {
        dependsIntellijModulesLang()
        depends(pluginOne.id!!, "one.xml") {
          dependsIntellijModulesLang()
          extensions(
            """<applicationService serviceInterface="${MyPersistentComponent::class.java.name}" 
              |serviceImplementation="${MyPersistentComponentImpl::class.java.name}"/>""".trimMargin()
          )
        }
      }).use {
        assertThat(app.getService(MyPersistentComponent::class.java)).isNotNull()
      }
      assertThat(PluginManagerCore.getPlugin(PluginId.getId(pluginTwoId))).isNull()
      assertThat(app.getService(MyPersistentComponent::class.java)).isNull()
    }
  }

  @Test
  fun loadOptionalDependencyListener() {
    receivedNotifications.clear()
    receivedNotifications2.clear()

    val pluginTwo = plugin("optionalDependencyListener-two") {
      packagePrefix = "optionalDependencyListener-two"
      dependsIntellijModulesLang()
    }
    val pluginDescriptor = plugin("optionalDependencyListener-one") {
      packagePrefix = "org.foo.one"
      dependsIntellijModulesLang()
      content {
        module("intellij.org.foo") {
          packagePrefix = "org.foo"
          applicationListeners = """<listener class="${MyUISettingsListener2::class.java.name}" topic="com.intellij.ide.ui.UISettingsListener"/>"""
          dependencies { plugin(pluginTwo.id!!) }
        }
      }
      applicationListeners = """<listener class="${MyUISettingsListener::class.java.name}" topic="com.intellij.ide.ui.UISettingsListener"/>"""
    }
    loadPluginWithText(pluginDescriptor).use {
      val app = ApplicationManager.getApplication()
      app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertThat(receivedNotifications).hasSize(1)

      loadPluginWithText(pluginTwo).use {
        app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
        assertThat(receivedNotifications).hasSize(2)
        assertThat(receivedNotifications2).hasSize(1)
      }

      app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertThat(receivedNotifications).hasSize(3)
      assertThat(receivedNotifications2).hasSize(1)
    }
  }

  @Test
  fun loadOptionalDependencyEP() {
    val pluginTwo = plugin("optionalDependencyListener-two") { dependsIntellijModulesLang() }
    val pluginTwoDisposable = loadPluginWithText(pluginTwo)
    try {
      val pluginOneDisposable = loadPluginWithText(plugin("optionalDependencyListener-one") {
        dependsIntellijModulesLang()
        depends(pluginTwo.id!!, "two.xml") {
          dependsIntellijModulesLang()
          extensionPoints = """<extensionPoint qualifiedName="one.foo" interface="java.lang.Runnable" dynamic="true"/>"""
          extensions("""<foo implementation="${MyRunnable::class.java.name}"/>""", "one")
        }
      })
      Disposer.dispose(pluginOneDisposable)
    }
    finally {
      Disposer.dispose(pluginTwoDisposable)
    }
  }

  @Test
  fun loadOptionalDependencyEPAdjacentDescriptor() {
    val pluginTwo = plugin("optionalDependencyListener-two") { dependsIntellijModulesLang() }
    val pluginThree = plugin("optionalDependencyListener-three") { dependsIntellijModulesLang() }
    val pluginTwoDisposable = loadPluginWithText(pluginTwo)
    val pluginThreeDisposable = loadPluginWithText(pluginThree)
    try {
      val pluginDescriptor = plugin("optionalDependencyListener-one") {
        dependsIntellijModulesLang()
        depends(pluginTwo.id!!, "two.xml") {
          dependsIntellijModulesLang()
          extensionPoints = """<extensionPoint qualifiedName="one.foo" interface="java.lang.Runnable" dynamic="true"/>"""
        }
        depends(pluginThree.id!!, "three.xml") {
          dependsIntellijModulesLang()
          extensions("""<foo implementation="${MyRunnable::class.java.name}"/>""", "one")
        }
      }
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
    loadPluginWithText(plugin {
      dependsIntellijModulesLang()
      extensions("""<projectService serviceImplementation="${MyProjectService::class.java.name}"/>""")
    }).use {
      assertThat(project.getService(MyProjectService::class.java)).isNotNull()
    }
  }

  @Test
  fun extensionOnServiceDependency() {
    val project = projectRule.project
    StartupManagerImpl.addActivityEpListener(project)
    loadExtensionWithText("""
      <postStartupActivity implementation="${MyStartupActivity::class.java.name}"/>
      <projectService serviceImplementation="${MyProjectService::class.java.name}"/>
    """).use {
      assertThat(project.service<MyProjectService>().executed).isTrue()
    }
  }

  @Test
  fun unloadEPWithDefaultAttributes() {
    loadExtensionWithText(
      "<globalInspection implementationClass=\"${MyInspectionTool::class.java.name}\" cleanupTool=\"false\"/>").use {
      assertThat(InspectionEP.GLOBAL_INSPECTION.extensionList.any { it.implementationClass == MyInspectionTool::class.java.name }).isTrue()
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
        </intentionAction>""")
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
    Configurable.PROJECT_CONFIGURABLE.addChangeListener(project, {
      checked.incrementAndGet()
    }, listenerDisposable)

    val disposable = loadExtensionWithText("<projectConfigurable instance=\"${MyConfigurable::class.java.name}\" displayName=\"foo\"/>")
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
    var disposable = loadExtensionWithText(textToLoad)
    Disposer.dispose(disposable)

    UIUtil.dispatchAllInvocationEvents()
    disposable = loadExtensionWithText(textToLoad)
    Disposer.dispose(disposable)
  }

  @Test
  fun disableWithoutRestart() {
    val plugin = plugin("disableWithoutRestart") {
      dependsIntellijModulesLang()
      extensions("""<applicationService serviceInterface="${MyPersistentComponent::class.java.name}"
        |serviceImplementation="${MyPersistentComponentImpl::class.java.name}"/>""".trimMargin())
    }
    val disposable = loadPluginWithText(plugin)
    val app = ApplicationManager.getApplication()
    assertThat(app.getService(MyPersistentComponent::class.java)).isNotNull()
    try {
      val pluginDescriptor = PluginManagerCore.getPlugin(PluginId.getId(plugin.id!!))!!

      val disabled = PluginEnabler.getInstance().disable(listOf(pluginDescriptor))
      assertThat(disabled).isTrue()
      assertThat(pluginDescriptor.isEnabled).isFalse()
      assertThat(app.getService(MyPersistentComponent::class.java)).isNull()

      val enabled = PluginEnabler.getInstance().enable(listOf(pluginDescriptor))
      assertThat(enabled).isTrue()
      assertThat(pluginDescriptor.isEnabled).isTrue()
      assertThat(app.getService(MyPersistentComponent::class.java)).isNotNull()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun canUnloadNestedOptionalDependency() {
    val bar = plugin("bar") {
      dependsIntellijModulesLang()
      extensionPoints = """<extensionPoint qualifiedName="foo.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP"/>"""
    }
    val quux = plugin("quux") { dependsIntellijModulesLang() }
    val main = plugin("main") {
      dependsIntellijModulesLang()
      depends(bar.id!!, "bar.xml") {
        dependsIntellijModulesLang()
        depends(quux.id!!, "quux.xml") {
          dependsIntellijModulesLang()
          extensions("""<barExtension key="foo" implementationClass="y"/>""", "foo")
        }
      }
    }
    loadPluginWithText(bar).use {
      loadPluginWithText(quux).use {
        val descriptor = loadDescriptorInTest(main, pluginsDir)
        setPluginClassLoaderForMainAndSubPlugins(descriptor, DynamicPluginsTest::class.java.classLoader)
        assertThat(DynamicPlugins.checkCanUnloadWithoutRestart(descriptor)).isEqualTo(
          "Plugin ${main.id} is not unload-safe because of extension to non-dynamic EP foo.barExtension in optional dependency on ${quux.id} in optional dependency on ${bar.id}")
      }
    }
  }

  @Test
  fun `registry access of key from same plugin`() {
    /**
     * This is tricky and possibly flaky. The point here is to make sure that registry keys, that are declared in the same config,
     * are initialized before other extensions: there might be tricky cases of extension point listeners triggering some plugin's internal
     * logic that tries to access the registry.
     * See https://youtrack.jetbrains.com/issue/IDEA-254324
     *
     * So the problem in the current implementation is that extensions are initialized in an order that
     * HashMap<String (extension point name), Collection<ExtensionDescriptor>> gives. So if there is an extension point with such a name
     * that precedes com.intellij.registryKey in this map, it will be initialized before the registry keys.
     *
     * This scenario might become obsolete if HashMap implementation changes so that the order of keys changes or if the plugin initialization
     * code changes. This test breaks as of revision 6be3a648dd07e4f675d40ab9553709446b06717c.
     */
    val rnd = Random(239)
    val pool: List<Char> = ('a'..'z') + ('A'..'Z')

    val idParent = "test.batya"
    val antiHashMapKeys = listOf(
      "roGjzgGTyI", // this one goes before com.intellij.registryKey
      "OMEBtHDTCw" // this one goes after com.intellij.registryKey, so the test is green in this case
    ) + (0..10).map { (0 until 10).map { pool.random(rnd) }.joinToString("") }

    for (antiHashMap in antiHashMapKeys) {
      val parentPlugin = plugin(idParent) {
        dependsIntellijModulesLang()
        extensionPoints = """<extensionPoint qualifiedName="$idParent.$antiHashMap" interface="java.lang.Runnable" dynamic="true"/>"""
      }

      val id = "test.plugin"
      loadPluginWithText(parentPlugin).use {
        val ep = ApplicationManager.getApplication().extensionArea.getExtensionPoint<Runnable>("$idParent.$antiHashMap")
        ep.addChangeListener({ ep.extensionList.forEach(Runnable::run) }, it)
        val plugin = plugin(id) {
          dependsIntellijModulesLang()
          depends(parentPlugin.id!!, "parent.xml") {
            extensions("""<registryKey key="test.plugin.registry.key" defaultValue="true" description="sample text"/>""")
            extensions("""<$antiHashMap implementation="${MyServiceAccessor::class.java.name}"/>""", idParent)
            extensions("""<applicationService serviceImplementation="${MyRegistryAccessor::class.java.name}"/>""")
          }
        }

        loadPluginWithText(plugin).use {
          check(service<MyRegistryAccessor>().invocations == 1)
        }
      }
    }
  }

  @Test
  fun `incompatible plugins cannot be enabled dynamically`() {
    // Create an incompatible plugin
    val incompatiblePlugin = plugin("incompatiblePlugin") {
      dependsIntellijModulesLang()
      version = "1.0"
      sinceBuild = "999.0"
      untilBuild = "999.999"
      extensions("""<applicationService serviceInterface="${MyPersistentComponent::class.java.name}"
        |serviceImplementation="${MyPersistentComponentImpl::class.java.name}"/>""".trimMargin())
    }
    val descriptor = loadDescriptorInTest(incompatiblePlugin, pluginsDir)

    PluginEnabler.getInstance().disable(listOf(descriptor))
    assertThat(PluginEnabler.getInstance().isDisabled(PluginId.getId(incompatiblePlugin.id!!))).isTrue()

    // This should return false since the plugin is incompatible
    val result = PluginEnabler.getInstance().enable(listOf(descriptor))

    assertThat(result).isFalse() // restart required

    val app = ApplicationManager.getApplication()
    assertThat(app.getService(MyPersistentComponent::class.java)).isNull() // plugin is not loaded

    // we will fail to load it on the next start, this is required to make a simultaneous Update possible
    assertThat(PluginEnabler.getInstance().isDisabled(PluginId.getId(incompatiblePlugin.id!!))).isFalse()
  }

  @Test
  @TestFor(issues = ["IJPL-183884"])
  fun `initial loading errors are cleared after successful dynamic plugin loading`() {
    val barPluginPath = pluginsDir.resolve("bar")
    val fooPluginPath = pluginsDir.resolve("foo")

    // initial descriptor loading
    plugin("bar") {}.buildDir(barPluginPath)
    plugin("foo") { depends("bar") }.buildDir(fooPluginPath)
    PluginSetTestBuilder.fromPath(pluginsDir).withDisabledPlugins("bar").build()

    val barPluginId = PluginId.getId("bar")
    val fooPluginId = PluginId.getId("foo")
    assertNoLoadingErrors(barPluginId)
    assertDisabledDependencyLoadingError(pluginId = fooPluginId, dependencyId = barPluginId)
    assertThat(PluginManagerCore.getPluginSet()).doesNotHaveEnabledPlugins("foo", "bar")

    // enable dependency
    loadPluginInTest(barPluginPath) {
      assertThat(PluginManagerCore.getPluginSet()).hasEnabledPlugins("bar")
      assertThat(PluginManagerCore.getPluginSet()).doesNotHaveEnabledPlugins("foo")
      assertNoLoadingErrors(barPluginId)
      assertDisabledDependencyLoadingError(pluginId = fooPluginId, dependencyId = barPluginId)

      // enable dependent with dependency enabled beforehand
      loadPluginInTest(fooPluginPath) {
        assertThat(PluginManagerCore.getPluginSet()).hasEnabledPlugins("foo", "bar")
        assertNoLoadingErrors(barPluginId)
        assertNoLoadingErrors(fooPluginId)
      }
    }
  }

  @Test
  fun `enabling a plugin will not load actions form a module with an unsatisfied dependency`() {
    val fooPluginPath = pluginsDir.resolve("foo")
    val barPluginPath = pluginsDir.resolve("bar")

    plugin("bar") {}.buildDir(barPluginPath)
    plugin("foo") {
      content {
        module("foo.a") {
          dependencies {
            plugin("bar")
          }
        }
        module("foo.b") {
          dependencies {
            plugin("bar")
            plugin("baz")
          }
          actions = """
            <action id="foo.b.action" class="${MyAction::class.java.name}"/>
          """
        }
      }
    }.buildDir(fooPluginPath)

    PluginSetTestBuilder.fromPath(pluginsDir).withDisabledPlugins("bar").build()
    loadPluginInTest(fooPluginPath) {
      loadPluginInTest(barPluginPath) {
        assertThat(PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId("foo.b"))).isNull()
        assertThat(ActionManager.getInstance().getAction("foo.b.action")).isNull()
      }
    }
  }

  @Test
  fun `we do not try to load an implementation-details plugin when it wants to enable an implementation-details module `() {
    val fooPluginPath = pluginsDir.resolve("foo")
    val barPluginPath = pluginsDir.resolve("bar")

    plugin("bar") {}.buildDir(barPluginPath)
    plugin("foo") {
      implementationDetail = true
      content {
        module("foo.a") {
          dependencies {
            plugin("bar")
          }
        }
      }
    }.buildDir(fooPluginPath)

    PluginSetTestBuilder.fromPath(pluginsDir).withDisabledPlugins("bar").build()
    loadPluginInTest(fooPluginPath) {
      loadPluginInTest(barPluginPath) {
        assertThat(PluginManagerCore.getPluginSet().buildContentModuleIdMap().contains(PluginModuleId("foo.a"))).isTrue
      }
    }
  }
}

@InternalIgnoreDependencyViolation
private class MyServiceAccessor : Runnable {
  override fun run() {
    service<MyRegistryAccessor>().accessRegistry()
  }
}

private class MyRegistryAccessor {
  var invocations: Int = 0

  fun accessRegistry() {
    @Suppress("UnresolvedPluginConfigReference")
    check(Registry.get("test.plugin.registry.key").asBoolean())
    invocations++
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

private interface MyPersistentComponent {

  var data: String?
}

@State(name = "MyTestState", storages = [Storage("other.xml")], allowLoadInTests = true)
private class MyPersistentComponentImpl : MyPersistentComponent,
                                          PersistentStateComponent<MyPersistentState> {

  private var _state = MyPersistentState("")

  override var data: String?
    get() = _state.stateData
    set(value) {
      _state.stateData = value
    }

  override fun getState() = _state

  fun setState(state: MyPersistentState) {
    _state = state
  }

  override fun loadState(state: MyPersistentState) {
    this.state = state
  }
}

@InternalIgnoreDependencyViolation
private class MyStartupActivity : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    val service = project.getService(MyProjectService::class.java)
    if (service != null) {
      service.executed = true
    }
  }
}

@InternalIgnoreDependencyViolation
private class MyProjectService {
  companion object {
    val LOG = logger<MyProjectService>()
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

@Suppress("DialogTitleCapitalization")
private class MyIntentionAction : IntentionAction {
  override fun startInWriteAction() = false
  override fun getFamilyName() = "foo"
  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?) = false
  override fun getText(): String = "foo"
  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
  }
}

private class MyRunnable : Runnable {
  override fun run() {
  }
}

private class MyModuleConfigurationEditorProvider : ModuleConfigurationEditorProvider {
  override fun createEditors(state: ModuleConfigurationState): Array<ModuleConfigurationEditor> = emptyArray()
}

private inline fun runAndCheckThatNoNewPlugins(block: () -> Unit) {
  val expectedPluginIds = lexicographicallySortedPluginIds()
  block()
  assertThat(lexicographicallySortedPluginIds()).isEqualTo(expectedPluginIds)
}

private fun lexicographicallySortedPluginIds() = PluginManagerCore.loadedPlugins.toSortedSet(compareBy { it.pluginId })

private fun findEnabledModuleByName(id: String) = PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId(id))

private fun assertModuleIsNotLoaded(moduleName: String) {
  assertThat(findEnabledModuleByName(moduleName)).isNull()
}

private fun assertModuleIsLoaded(moduleName: String) {
  assertThat(findEnabledModuleByName(moduleName)?.pluginClassLoader).isNotNull()
}

private fun loadPluginInTest(pluginPath: Path, actionWithPluginLoaded: () -> Unit) {
  val descriptor = loadDescriptorInTest(pluginPath)
  try {
    assertThat(DynamicPlugins.loadPlugin(pluginDescriptor = descriptor)).isTrue()
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
    actionWithPluginLoaded()
  }
  finally {
    unloadAndUninstallPlugin(descriptor)
  }
}

private fun assertNoLoadingErrors(pluginId: PluginId) {
  val error = PluginManagerCore.getLoadingError(pluginId)
  assertThat(error).isNull()
}

private fun assertDisabledDependencyLoadingError(pluginId: PluginId, dependencyId: PluginId) {
  val error = PluginManagerCore.getLoadingError(pluginId)
  assertThat(error).isNotNull().isInstanceOf(PluginDependencyIsDisabled::class.java)
  val disabledDependency = (error as PluginDependencyIsDisabled).dependencyId
  assertThat(disabledDependency).isNotNull().isEqualTo(dependencyId)
}
