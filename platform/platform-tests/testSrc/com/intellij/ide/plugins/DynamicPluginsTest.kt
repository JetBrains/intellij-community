// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.ide.actions.ContextHelpAction
import com.intellij.ide.plugins.testPluginSrc.IJPL207058.DefaultService
import com.intellij.ide.plugins.testPluginSrc.IJPL207058.ServiceInterface
import com.intellij.ide.plugins.testPluginSrc.IJPL207058.module.OverriddenService
import com.intellij.ide.plugins.testPluginSrc.IJPL233642.FooCore
import com.intellij.ide.plugins.testPluginSrc.IJPL233642.FooCoreAppActivity
import com.intellij.ide.plugins.testPluginSrc.bar.BarAction
import com.intellij.ide.plugins.testPluginSrc.bar.BarService
import com.intellij.ide.plugins.testPluginSrc.foo.FooAction
import com.intellij.ide.plugins.testPluginSrc.foo.bar.FooBarAction
import com.intellij.ide.plugins.testPluginSrc.foo.bar.FooBarService
import com.intellij.ide.plugins.testPluginSrc.foo.ep.FooExtension
import com.intellij.ide.plugins.testPluginSrc.foo.ep.FooExtensionService
import com.intellij.ide.plugins.testPluginSrc.foo.epImpl.FooExtensionImpl
import com.intellij.ide.plugins.testPluginSrc.projectService.MyProjectService
import com.intellij.ide.plugins.testPluginSrc.projectService.MyStartupActivity
import com.intellij.ide.plugins.testPluginSrc.registryAccess.MyRegistryAccessor
import com.intellij.ide.plugins.testPluginSrc.registryAccess.MyRegistryAccessorService
import com.intellij.ide.plugins.testPluginSrc.testPSC.MyPersistentComponent
import com.intellij.ide.plugins.testPluginSrc.testPSC.impl.MyPersistentComponentImpl
import com.intellij.ide.plugins.testPluginSrc.uiSettingsListener.MyUISettingsListener
import com.intellij.ide.plugins.testPluginSrc.uiSettingsListener.MyUISettingsListenerService
import com.intellij.ide.plugins.testPluginSrc.uiSettingsListener.foo.MyFooUISettingsListener
import com.intellij.ide.plugins.testPluginSrc.uiSettingsListener.foo.MyFooUISettingsListenerService
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
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.pluginSystem.testFramework.buildPluginSet
import com.intellij.platform.testFramework.loadDescriptorInTest
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.platform.testFramework.plugins.PluginSpec
import com.intellij.platform.testFramework.plugins.PluginTestHandle
import com.intellij.platform.testFramework.plugins.action
import com.intellij.platform.testFramework.plugins.applicationListener
import com.intellij.platform.testFramework.plugins.applicationService
import com.intellij.platform.testFramework.plugins.applicationServiceImpl
import com.intellij.platform.testFramework.plugins.buildMainJar
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.extension
import com.intellij.platform.testFramework.plugins.extensionPoint
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.includePackageClassFiles
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.platform.testFramework.setPluginClassLoaderForMainAndSubPlugins
import com.intellij.platform.testFramework.unloadAndUninstallPlugin
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.ui.switcher.ShowQuickActionPopupAction
import com.intellij.util.KeyedLazyInstanceEP
import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import java.lang.ref.WeakReference
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Suppress("UnresolvedPluginConfigReference")
@RunsInEdt
class DynamicPluginsTest {
  // per test
  @Rule
  @JvmField
  val projectRule = ProjectRule()

  @Rule
  @JvmField
  val testDisposable = DisposableRule()

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
  fun `app level listeners are loaded with plugin load`() {
    val app = ApplicationManager.getApplication()
    app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
    val pluginSet = buildPluginSet(pluginsDir) {
      plugin("listeners") {
        applicationListener<MyUISettingsListener, UISettingsListener>()
        includePackageClassFiles<MyUISettingsListener>()
      }
    }
    val plugin = pluginSet.getEnabledPlugin("listeners")
    loadPluginInTest(plugin) {
      val listenerTriggered = AtomicBoolean()
      val handle = application.getTestHandleService<MyUISettingsListenerService, _, _>(plugin)!!
      handle.test { listenerTriggered.set(true) }
      app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertThat(listenerTriggered.get()).isTrue()
      handle.test { error("supposed to be unloaded") }
    }
    app.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
  }

  @Test
  fun `loading of a plugin also loads dependent content modules of other plugins`() {
    assumeNewSupportEnabled()
    val pluginSet = buildPluginSet(pluginsDir) {
      plugin("foo") { }
      plugin("listeners") {
        content {
          module("listeners.foo") {
            dependencies { plugin("foo") }
            applicationListener<MyFooUISettingsListener, UISettingsListener>()
            includePackageClassFiles<MyFooUISettingsListener>()
          }
        }
        applicationListener<MyUISettingsListener, UISettingsListener>()
        includePackageClassFiles<MyUISettingsListener>()
      }
    }
    val (foo, listeners) = pluginSet.getEnabledPlugins("foo", "listeners")
    loadPluginInTest(listeners) {
      val mainListenerHandle = application.getTestHandleService<MyUISettingsListenerService, _, _>(listeners)!!
      val mainListenerInvokedCount = AtomicInteger(0)
      val fooListenerInvokedCount = AtomicInteger(0)
      mainListenerHandle.test { mainListenerInvokedCount.incrementAndGet() }
      application.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertThat(mainListenerInvokedCount.get()).isEqualTo(1)
      loadPluginInTest(foo) {
        val fooListenerHandle = application.getTestHandleService<MyFooUISettingsListenerService, _, _>(listeners.contentModules.first())!!
        fooListenerHandle.test { fooListenerInvokedCount.incrementAndGet() }
        application.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
        assertThat(mainListenerInvokedCount.get()).isEqualTo(2)
        assertThat(fooListenerInvokedCount.get()).isEqualTo(1)
      }
      application.messageBus.syncPublisher(UISettingsListener.TOPIC).uiSettingsChanged(UISettings())
      assertThat(mainListenerInvokedCount.get()).isEqualTo(3)
      assertThat(fooListenerInvokedCount.get()).isEqualTo(1)
    }
  }

  @Test
  fun `PSC data is preserved between unload and load`() {
    val data = "hello world"
    val pluginSet = buildPluginSet(pluginsDir) {
      plugin("psc") {
        applicationService<MyPersistentComponentImpl, MyPersistentComponent>()
        includePackageClassFiles<MyPersistentComponentImpl>()
        includePackageClassFiles<MyPersistentComponent>()
      }
    }
    val plugin = pluginSet.getEnabledPlugin("psc")
    loadPluginInTest(plugin) {
      val handle = application.getTestHandleService<MyPersistentComponent, _, _>(plugin)!!
      handle.test(data)
    }
    loadPluginInTest(plugin) {
      val handle = application.getTestHandleService<MyPersistentComponent, _, _>(plugin)!!
      assertThat(handle.test(null)).isEqualTo(data)
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
          dependencies { plugin(pluginTwoBuilder.id!!) }
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
  fun `cross-dependent content modules loading with classloading check`() {
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("foo") {
        packagePrefix = FooBarService::class.java.packageName.let { it.substring(0, it.lastIndexOf('.')) }
        content(namespace = "test_ns") {
          module("foo.bar") {
            packagePrefix = FooBarService::class.java.packageName
            dependencies { plugin("bar") }
          }
        }
        includePackageClassFiles<FooBarService>()
      }
      plugin("bar") {
        content(namespace = "test_ns") {
          module("bar.foo") {
            packagePrefix = BarService::class.java.packageName
            dependencies { plugin("foo") }
          }
        }
        includePackageClassFiles<BarService>()
      }
    }
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    val fooBar = foo.contentModules.first()
    val barFoo = bar.contentModules.first()

    loadPluginInTest(bar) {
      loadPluginInTest(foo) {
        assertThat(fooBar.loadClassInsideSelf<FooBarService>()).isNotNull
        assertThat(barFoo.loadClassInsideSelf<BarService>()).isNotNull
      }
    }
  }

  @Test
  fun `optional plugin dependency loading`() {
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("foo") {
        depends("bar", "bar.xml") {
          applicationServiceImpl<FooBarService>()
        }
        includePackageClassFiles<FooBarService>()
      }
      plugin("bar") {
        applicationServiceImpl<BarService>()
        includePackageClassFiles<BarService>()
      }
    }
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")

    loadPluginInTest(foo) {
      loadPluginInTest(bar) {
        val barService = application.getTestHandleService<BarService, _, _>(bar)!!
        barService.test(Unit)
        val fooBarClass = foo.loadClassInsideSelf<FooBarService>()!! // loaded because packed into the same jar with the main descriptor
        if (isNewSupportEnabled()) {
          assertThat(application.getService(fooBarClass)).isNotNull()
          assertThat(foo.dependencies.first().subDescriptor!!.isMarkedForLoading).isTrue
          assertThat(foo.dependencies.first().subDescriptor!!.pluginClassLoader).isNotNull()
        } else {
          // why was it like that...?
          assertThat(application.getService(fooBarClass)).isNull()
          assertThat(foo.dependencies.first().subDescriptor!!.isMarkedForLoading).isFalse
          assertThat(foo.dependencies.first().subDescriptor!!.pluginClassLoader).isNull()
        }
      }
    }
  }

  @Test
  fun `separate content module jar unloading`() {
    val fooDir = plugin("foo") {
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
    }.installAt(pluginsDir)
    val barJar = pluginsDir.resolve("bar.jar")
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
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("foo") {
        content {
          module("foo.emb", loadingRule = ModuleLoadingRuleValue.EMBEDDED) {
            isSeparateJar = true
            extensionPoint<FooExtension>(FooExtension.EP_FQN, dynamic = true)
            includePackageClassFiles<FooExtension>()
          }
        }
        extension<FooExtensionImpl>(FooExtension.EP_FQN)
        includePackageClassFiles<FooExtensionImpl>()
      }
    }
    val foo = pluginSet.getEnabledPlugin("foo")
    val fooEmb = foo.contentModules.first()
    loadPluginInTest(foo) {
      assertThat(application.extensionArea.getExtensionPoint<Any>(FooExtension.EP_FQN).extensions.size).isEqualTo(1)
      val epService = application.getTestHandleService<FooExtensionService, _, _>(fooEmb)!!
      epService.test(Unit)
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
      content(namespace = "test_ns") {
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
      content(namespace = "test_ns") {
        module("intellij.bar.foo") {
          packagePrefix = "com.intellij.bar.foo"
          moduleVisibility = ModuleVisibilityValue.PUBLIC
          dependencies { plugin(foo.id!!) }
        }
      }
    }
    val baz = plugin("com.intellij.baz") {
      packagePrefix = "com.intellij.baz"
      dependsIntellijModulesLang()
      content(namespace = "test_ns") {
        module("intellij.baz.foo") {
          packagePrefix = "com.intellij.baz.foo"
          dependencies {
            plugin(foo.id!!)
            plugin(bar.id!!)
            module("intellij.bar.foo", namespace = "test_ns")
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
    val foo = plugin("foo") {
      packagePrefix = "foo"
    }
    val bar = plugin("bar") {
      packagePrefix = "bar"
      content(namespace = "test_ns") {
        module("bar.foo") {
          packagePrefix = "bar.foo"
          moduleVisibility = ModuleVisibilityValue.PUBLIC
          dependencies { plugin(foo.id!!) }
          extensions("""<multiHostInjector implementation="bar.foo.InjectorImpl"/>""")
        }
      }
    }
    val baz = plugin("baz") {
      packagePrefix = "baz"
      dependsIntellijModulesLang()
      content(namespace = "test_ns") {
        module("baz.bar") {
          packagePrefix = "baz.bar"
          dependencies { plugin(bar.id!!) }
        }
        module("baz.bar.foo") {
          packagePrefix = "baz.bar.foo"
          dependencies {
            plugin(foo.id!!)
            module("bar.foo", namespace = "test_ns")
            module("baz.bar")
          }
          extensions("""<multiHostInjector implementation="baz.bar.foo.InjectorImpl"/>""")
        }
      }
    }

    fun assertForModules(assertion: (String) -> Unit) {
      listOf(
        "bar.foo",
        "baz.bar",
        "baz.bar.foo",
      ).forEach(assertion)
    }

    val ep = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getPoint(projectRule.project) as ExtensionPointImpl<MultiHostInjector>
    val coreInjectorsCount = ep.size()

    loadPluginWithText(pluginSpec = baz).use {
      assertForModules(::assertModuleIsNotLoaded)
      assertThat(ep.size()).isEqualTo(coreInjectorsCount)

      loadPluginWithText(pluginSpec = foo).use {
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
  fun `service registration from dynamic loading of optional depends`() {
    val pluginSet = buildPluginSet(pluginsDir) {
      plugin("foo") {
        includePackageClassFiles<MyPersistentComponent>()
      }
      plugin("bar") {
        depends("foo", "foo.xml") {
          applicationService<MyPersistentComponentImpl, MyPersistentComponent>()
          includePackageClassFiles<MyPersistentComponentImpl>()
        }
      }
    }
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")

    loadPluginInTest(foo) {
      assertThat(application.getTestHandleService<MyPersistentComponent, _, _>(foo)).isNull()
      loadPluginInTest(bar) {
        assertThat(application.getTestHandleService<MyPersistentComponent, _, _>(foo)).isNotNull()
        assertThat(PluginManagerCore.getPlugin(PluginId.getId("bar"))).isNotNull()
      }
      assertThat(PluginManagerCore.getPlugin(PluginId.getId("bar"))).isNull()
      assertThat(application.getTestHandleService<MyPersistentComponent, _, _>(foo)).isNull()
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
  fun `project service loads`() {
    val project = projectRule.project
    val pluginSet = buildPluginSet(pluginsDir) {
      plugin("plugin") {
        extensions("""<projectService serviceImplementation="${MyProjectService::class.java.name}"/>""")
        includePackageClassFiles<MyProjectService>()
      }
    }
    val plugin = pluginSet.getEnabledPlugin("plugin")
    loadPluginInTest(plugin) {
      assertThat(project.getTestHandleService<MyProjectService, _, _>(plugin)).isNotNull()
    }
  }

  @Test
  fun `project service can be acquired from extension listener - StartupActivity`() {
    val project = projectRule.project
    StartupManagerImpl.addActivityEpListener(project)
    val pluginSet = buildPluginSet(pluginsDir) {
      plugin("plugin") {
        extensions("""<postStartupActivity implementation="${MyStartupActivity::class.java.name}"/>""")
        extensions("""<projectService serviceImplementation="${MyProjectService::class.java.name}"/>""")
        includePackageClassFiles<MyProjectService>()
      }
    }
    val plugin = pluginSet.getEnabledPlugin("plugin")
    loadPluginInTest(plugin) {
      val handle = project.getTestHandleService<MyProjectService, _, _>(plugin)!!
      assertThat(handle.test(Unit)).isTrue()
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
  fun `service load and unload without restart through PluginEnabler`() {
    val pluginSet = buildPluginSet(pluginsDir) {
      plugin("disableWithoutRestart") {
        applicationService<MyPersistentComponentImpl, MyPersistentComponent>()
        includePackageClassFiles<MyPersistentComponentImpl>()
        includePackageClassFiles<MyPersistentComponent>()
      }
    }
    val plugin = pluginSet.enabledPlugins.first()
    loadPluginInTest(plugin) {
      val weakHandleClass = WeakReference(plugin.loadClassInsideSelf<MyPersistentComponent>())
      val disabled = PluginEnabler.getInstance().disable(listOf(plugin))
      assertThat(disabled).isTrue()
      assertThat(plugin.isEnabled).isFalse()
      val handleClass = weakHandleClass.get()
      if (handleClass != null) assertThat(application.getService(handleClass)).isNull()

      val enabled = PluginEnabler.getInstance().enable(listOf(plugin))
      assertThat(enabled).isTrue()
      assertThat(plugin.isEnabled).isTrue()
      assertThat(application.getService(plugin.loadClassInsideSelf<MyPersistentComponent>()!!)).isNotNull()
    }
  }

  @Test
  fun `loading of plugin with an extension of non-dynamic EP is prohibited`() {
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("bar") {
        extensionPoints = """<extensionPoint qualifiedName="foo.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP"/>"""
      }
      plugin("quux") {}
      plugin("main") {
        depends("bar", "bar.xml") {
          depends("quux", "quux.xml") {
            extensions("""<barExtension key="foo" implementationClass="y"/>""", "foo")
          }
        }
      }
    }
    val (bar, quux, main) = pluginSet.getEnabledPlugins("bar", "quux", "main")
    loadPluginInTest(bar) {
      loadPluginInTest(quux) {
        if (isNewSupportEnabled()) {
          assertThat(DynamicPlugins.validateCanLoadWithoutRestart(main)).isEqualTo(
            "<depends> config 'quux.xml' of plugin main cannot be loaded/unloaded dynamically because it uses non-dynamic extension point 'foo.barExtension' from ${bar.shortLogDescription}."
          )
        } else {
          setPluginClassLoaderForMainAndSubPlugins(main, DynamicPluginsTest::class.java.classLoader)
          assertThat(DynamicPlugins.validateCanLoadWithoutRestart(main)).isEqualTo(
            "Plugin '${main.pluginId}' is not unload-safe because of extension to non-dynamic EP 'foo.barExtension' in optional dependency on ${quux.pluginId} in optional dependency on ${bar.pluginId}"
          )
        }
      }
    }
  }

  @Test
  fun `loading of plugin with an extension of non-dynamic EP is prohibited - even if EP is registered in the same plugin`() {
    val epName = "one.foo"
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("nonDynamic") {
        extensionPoints = """<extensionPoint qualifiedName="$epName" interface="java.lang.Runnable"/>"""
        extensions("""<foo implementation="${MyRunnable::class.java.name}"/>""", "one")
      }
    }
    val plugin = pluginSet.getPlugin("nonDynamic")
    if (isNewSupportEnabled()) {
      assertThat(DynamicPlugins.validateCanLoadWithoutRestart(plugin))
        .isEqualTo("${plugin.shortLogDescription} cannot be loaded/unloaded dynamically because it uses non-dynamic extension point 'one.foo' from ${plugin.shortLogDescription}.")
    } else {
      assertThat(DynamicPlugins.validateCanLoadWithoutRestart(plugin))
        .isEqualTo("Plugin '${plugin.pluginId}' is not unload-safe because of extension to non-dynamic EP '$epName'")
    }
  }

  @Test
  fun `loading of plugin with an extension of dynamic EP is allowed - EP is registered in the same plugin`() {
    val epName = "one.foo"
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("dynamic") {
        extensionPoints = """<extensionPoint qualifiedName="$epName" interface="java.lang.Runnable" dynamic="true"/>"""
        extensions("""<foo implementation="${MyRunnable::class.java.name}"/>""", "one")
      }
    }
    val plugin = pluginSet.getPlugin("dynamic")
    assertThat(DynamicPlugins.validateCanLoadWithoutRestart(plugin)).isNull()
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
    val parentId = "test.batya"
    val childId = "test.plugin"
    val antiHashMapKeys = listOf(
      "roGjzgGTyI", // this one goes before com.intellij.registryKey
      "OMEBtHDTCw" // this one goes after com.intellij.registryKey, so the test is green in this case
    )

    for (antiHashMapKey in antiHashMapKeys) {
      logger<DynamicPluginsTest>().debug("key: $antiHashMapKey")

      val epFqn = "$parentId.$antiHashMapKey"
      val pluginSet = buildPluginSet(pluginsDir.resolve(antiHashMapKey)) {
        plugin(parentId) {
          extensionPoints = """<extensionPoint qualifiedName="$epFqn" interface="java.lang.Runnable" dynamic="true"/>"""
        }
        plugin(childId) {
          depends(parentId, "parent.xml") {
            extensions("""<registryKey key="test.plugin.registry.key" defaultValue="true" description="sample text"/>""")
            extensions("""<$antiHashMapKey implementation="${MyRegistryAccessor::class.qualifiedName}"/>""", parentId)
          }
          includePackageClassFiles<MyRegistryAccessor>()
        }
      }
      val (parent, child) = pluginSet.getEnabledPlugins(parentId, childId)
      loadPluginInTest(parent) {
        val ep = ApplicationManager.getApplication().extensionArea.getExtensionPoint<Runnable>(epFqn)
        ep.addChangeListener(Runnable { ep.extensionList.forEach(Runnable::run) }, testDisposable.disposable)
        loadPluginInTest(child) {
          val handle = application.getTestHandleService<MyRegistryAccessorService, _, _>(child)!!
          assertThat(handle.test(Unit)).isEqualTo(1)
        }
      }
    }
  }

  @Test
  fun `IJPL-233642 registry access of key from same plugin with multiple modules`() {
    StartupManagerImpl.addActivityEpListener(projectRule.project)
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("foo") {
        content {
          module("foo.core", loadingRule = ModuleLoadingRuleValue.EMBEDDED) {
            isSeparateJar = true
            extensions("""
            <postStartupActivity implementation="${FooCoreAppActivity::class.qualifiedName!!}"/>
          """.trimIndent())
            includePackageClassFiles<FooCoreAppActivity>()
          }
          module("foo.acp", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {
            dependencies {
              module("foo.core")
            }
            isSeparateJar = true
            extensions("""
            <registryKey defaultValue="true"
                 description="Foo foo"
                 key="foo.module.registry.key"/>
          """.trimIndent())
          }
        }
      }
    }
    val foo = pluginSet.getPlugin("foo")
    val fooCore = foo.contentModules.first { it.moduleId.name == "foo.core" }
    loadPluginInTest(foo) {
      val coreClass = application.getTestHandleService<FooCore, _, _>(fooCore)!!
      coreClass.test(Unit)
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
    // initial descriptor loading
    val barPluginPath = plugin("bar") {}.installAt(pluginsDir)
    val fooPluginPath = plugin("foo") { depends("bar") }.installAt(pluginsDir)
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
    val barPluginPath = plugin("bar") {}.installAt(pluginsDir)
    val fooPluginPath = plugin("foo") {
      content(namespace = "test_ns") {
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
    }.installAt(pluginsDir)

    PluginSetTestBuilder.fromPath(pluginsDir).withDisabledPlugins("bar").build()
    loadPluginInTest(fooPluginPath) {
      loadPluginInTest(barPluginPath) {
        assertThat(PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId("foo.b", "test_ns"))).isNull()
        assertThat(ActionManager.getInstance().getAction("foo.b.action")).isNull()
      }
    }
  }

  @Test
  fun `we do not try to load an implementation-details plugin when it wants to enable an implementation-details module `() {
    val barPluginPath = plugin("bar") {}.installAt(pluginsDir)
    val fooPluginPath = plugin("foo") {
      implementationDetail = true
      content(namespace = "test_ns") {
        module("foo.a") {
          dependencies {
            plugin("bar")
          }
        }
      }
    }.installAt(pluginsDir)

    PluginSetTestBuilder.fromPath(pluginsDir).withDisabledPlugins("bar").build()
    loadPluginInTest(fooPluginPath) {
      loadPluginInTest(barPluginPath) {
        assertThat(PluginManagerCore.getPluginSet().buildContentModuleIdMap().contains(PluginModuleId("foo.a", "test_ns"))).isTrue
      }
    }
  }

  @Test
  fun `IJPL-207058 dynamic load of a plugin with service overrides is declined`() {
    assumeNewSupportEnabled()
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("foo") {
        content {
          module("foo.module") {
            extensions("""
            <applicationService serviceInterface="${ServiceInterface::class.qualifiedName}" 
                                serviceImplementation="${OverriddenService::class.qualifiedName}"
                                overrides="true"/>
          """.trimIndent())
            includePackageClassFiles<OverriddenService>()
          }
        }
        extensions("""
        <applicationService serviceInterface="${ServiceInterface::class.qualifiedName}" 
                            serviceImplementation="${DefaultService::class.qualifiedName}"/>
      """.trimIndent())
        includePackageClassFiles<DefaultService>()
      }
    }
    val foo = pluginSet.getPlugin("foo")
    assertThat(DynamicPlugins.validateCanLoadWithoutRestart(foo)).isNotNull()
  }

  @Test
  fun `test ide-plugins-allow-dynamic-services-overrides registry flag`() {
    for (dynamicServiceOverridesAllowed in listOf(true, false)) {
      Registry.get("ide.plugins.allow.dynamic.services.overrides").setValue(dynamicServiceOverridesAllowed, testDisposable.disposable)
      val fooPath = plugin("foo") {
        extensions("""
        <applicationService serviceInterface="${ServiceInterface::class.qualifiedName}" 
                            serviceImplementation="${DefaultService::class.qualifiedName}"
                            open="true"/>
                            
        <applicationService serviceInterface="${ServiceInterface::class.qualifiedName}" 
                            serviceImplementation="${DefaultService::class.qualifiedName}"
                            overrides="true"/>
      """.trimIndent())
        includePackageClassFiles<DefaultService>()
      }.installAt(pluginsDir)
      val foo = loadDescriptorInTest(fooPath)
      assertThat(DynamicPlugins.validateCanLoadWithoutRestart(foo) == null).isEqualTo(dynamicServiceOverridesAllowed)
    }
  }

  @Test
  fun `IJPL-218420 dependent modules loading order is correct`() {
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("ai") {}
      plugin("completion") {
        content(namespace = "jetbrains") {
          module("completion.ai") {
            dependencies {
              plugin("ai")
            }
            moduleVisibility = ModuleVisibilityValue.PUBLIC
          }
        }
      }
      plugin("scala") {
        content(namespace = "jetbrains") {
          module("scala.ai.completion") {
            dependencies {
              plugin("ai")
              module("completion.ai")
            }
          }
        }
      }
    }
    val (ai, completion, scala) = pluginSet.getEnabledPlugins("ai", "completion", "scala")
    loadPluginInTest(completion) {
      val completionMainClassLoader = completion.pluginClassLoader
      assertThat(completionMainClassLoader).isNotNull()
      val completionAi = completion.contentModules.first()
      assertThat(completionAi.pluginClassLoader).isNull()
      assertThat(completionAi.isLoaded).isFalse
      loadPluginInTest(scala) {
        loadPluginInTest(ai) {
          val scalaAiCompletion = PluginManagerCore.getPlugin(PluginId("scala"))!!.contentModules[0] as ContentModuleDescriptor
          assert(PluginManagerCore.getPluginSet().isModuleEnabled(PluginModuleId("scala.ai.completion", PluginModuleId.JETBRAINS_NAMESPACE)))
          assertThat(scalaAiCompletion.isLoaded).isTrue

          assertThat(completion.pluginClassLoader).isEqualTo(completionMainClassLoader)
          assertThat(completionAi.isLoaded).isTrue
          // check that newly configured classloader refers to existing main completion classloader
          assertThat(completionAi).hasExactDirectParentClassloaders(completion, ai)
        }
      }
    }
  }

  @Test
  fun `IJPL-218420 dependent modules loading order is correct - transitive dependency`() {
    assumeNewSupportEnabled()
    val pluginSet = buildPluginSet(pluginsDir, configureClassLoaders = false) {
      plugin("ai") {}
      plugin("completion") {
        content(namespace = "jetbrains") {
          module("completion.ai") {
            dependencies {
              plugin("ai")
            }
            moduleVisibility = ModuleVisibilityValue.PUBLIC
          }
        }
      }
      plugin("scala") {
        content(namespace = "jetbrains") {
          module("scala.ai.completion") {
            dependencies {
              // plugin("ai") - no direct dependency
              module("completion.ai")
            }
          }
        }
      }
    }

    val (ai, completion, scala) = pluginSet.getEnabledPlugins("ai", "completion", "scala")
    loadPluginInTest(completion) {
      loadPluginInTest(scala) {
        loadPluginInTest(ai) {
          val scalaAiCompletion = PluginManagerCore.getPlugin(PluginId("scala"))!!.contentModules[0] as ContentModuleDescriptor
          assert(PluginManagerCore.getPluginSet().isModuleEnabled(PluginModuleId("scala.ai.completion", PluginModuleId.JETBRAINS_NAMESPACE)))
          assert(scalaAiCompletion.isLoaded)
          assert(completion.contentModules.first().isLoaded)
        }
      }
    }
  }

  private fun assumeNewSupportEnabled() {
    Assume.assumeTrue("new dynamic plugins support is enabled", isNewSupportEnabled())
  }

  private fun isNewSupportEnabled(): Boolean = DynamicPluginsSupport.getInstance() != null
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

private fun findEnabledModuleByName(id: String) = PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId(id, "test_ns"))

private fun assertModuleIsNotLoaded(moduleName: String) {
  assertThat(findEnabledModuleByName(moduleName)).isNull()
}

private fun assertModuleIsLoaded(moduleName: String) {
  assertThat(findEnabledModuleByName(moduleName)?.pluginClassLoader).isNotNull()
}

private fun loadPluginInTest(pluginPath: Path, actionWithPluginLoaded: () -> Unit) {
  return loadPluginInTest(loadDescriptorInTest(pluginPath), actionWithPluginLoaded)
}

private fun loadPluginInTest(plugin: PluginMainDescriptor, actionWithPluginLoaded: () -> Unit) {
  try {
    assertThat(DynamicPlugins.loadPlugin(pluginDescriptor = plugin)).isTrue()
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
    actionWithPluginLoaded()
  }
  finally {
    assertThat(unloadAndUninstallPlugin(plugin)).isTrue()
  }
}

private fun assertNoLoadingErrors(pluginId: PluginId) {
  val error = PluginManagerCore.getPluginNonLoadReason(pluginId)
  assertThat(error).isNull()
}

private fun assertDisabledDependencyLoadingError(pluginId: PluginId, dependencyId: PluginId) {
  val error = PluginManagerCore.getPluginNonLoadReason(pluginId)
  assertThat(error).isNotNull().isInstanceOfAny(PluginDependencyIsDisabled::class.java, PluginDependencyCannotBeLoaded::class.java)
  val disabledDependency = (error as? PluginDependencyIsDisabled)?.dependencyId ?: (error as? PluginDependencyCannotBeLoaded)!!.dependency.pluginId
  assertThat(disabledDependency).isNotNull().isEqualTo(dependencyId)
}

/** note: can't cast the output to [T], [T] can only be loaded by the isolated classloader of [descriptor] */
private inline fun <reified T: PluginTestHandle<I, O>, I, O> ComponentManager.getTestHandleService(descriptor: IdeaPluginDescriptorImpl): PluginTestHandle<I, O>? {
  val serviceClass = descriptor.loadClassInsideSelf<T>() ?: return null
  @Suppress("UNCHECKED_CAST")
  return getService(serviceClass) as PluginTestHandle<I, O>?
}