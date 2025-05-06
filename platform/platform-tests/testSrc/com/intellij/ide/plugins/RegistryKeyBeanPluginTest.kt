// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyBean
import com.intellij.openapi.util.registry.RegistryKeyDescriptor
import com.intellij.platform.testFramework.PluginBuilder
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.testFramework.*
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.Ksuid
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.mpp.atomics.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertTrue

@RunsInEdt
class RegistryKeyBeanPluginTest {

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()
  private val rootPath
    get() = inMemoryFs.fs.getPath("/")

  @Rule
  @JvmField
  val applicationRule = ApplicationRule()

  @Rule
  @JvmField
  val runInEdt = EdtRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  private val testDisposable: Disposable
    get() = disposableRule.disposable

  @Test
  fun `a dynamic key load is allowed`() {
    val key = "test.plugin.registry.key.1"
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey(key, defaultValue = true))

    loadPlugins(testDisposable, plugin1)

    assertTrue(Registry.get(key).asBoolean())
  }

  @Test
  fun `a static override of a non-existing key is allowed`() {
    val plugin = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = true, overrides = true))
    assertDoesNotThrow {
      emulateStaticRegistryLoad(plugin)
    }
  }

  @Test
  fun `a static override of a key is allowed`() {
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = false))
    val plugin2 = PluginBuilder.withModulesLang()
      .id("plugin2")
      .extensions(registryKey("my.key", defaultValue = true, overrides = true))
    val registry = emulateStaticRegistryLoad(plugin1, plugin2)
    registry["my.key"].shouldNotBeNull().defaultValue.toBoolean() shouldBe true
  }

  @Test
  fun `a dynamic override of an existing key is forbidden`() {
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = true))
    val plugin2 = PluginBuilder.withModulesLang()
      .id("plugin2")
      .extensions(registryKey("my.key", defaultValue = true, overrides = true))
    val message = executeAndReturnPluginLoadingWarning {
      loadPlugins(testDisposable, plugin1, plugin2)
    }
    message.shouldStartWith("A dynamically-loaded plugin plugin2 is forbidden to override the registry key my.key introduced by plugin1.")
  }

  @Test
  fun `loading of the key owner after overrider is a no-op`() {
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = false))
    val plugin2 = PluginBuilder.withModulesLang()
      .id("plugin2")
      .extensions(registryKey("my.key", defaultValue = true, overrides = true))
    val registry = emulateStaticRegistryLoad(plugin2, plugin1)
    registry["my.key"].shouldNotBeNull().defaultValue.toBoolean() shouldBe true
  }

  @Test
  fun `loading two owners of same key replaces the key value but reports a diagnostic`() {
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = true))
    val plugin2 = PluginBuilder.withModulesLang()
      .id("plugin2")
      .extensions(registryKey("my.key", defaultValue = false))

    lateinit var registry: Map<String, RegistryKeyDescriptor>
    val message = executeAndReturnPluginLoadingWarning {
      registry = emulateStaticRegistryLoad(plugin1, plugin2)
    }
    message.shouldStartWith("Conflicting registry key definition for key my.key: it was defined by plugin plugin1 but redefined by plugin plugin2.")

    registry["my.key"].shouldNotBeNull().defaultValue.toBoolean().shouldBe(false)
  }

  @Test
  fun `a dynamic load of a non-overriding key reports a diagnostic but works`() {
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = true))
    val plugin2 = PluginBuilder.withModulesLang()
      .id("plugin2")
      .extensions(registryKey("my.key", defaultValue = false))

    val message = executeAndReturnPluginLoadingWarning {
      loadPlugins(testDisposable, plugin1, plugin2)
    }
    message.shouldStartWith("Conflicting registry key definition for key my.key: it was defined by plugin plugin1 but redefined by plugin plugin2.")
    @Suppress("UnresolvedPluginConfigReference")
    Registry.get("my.key").asBoolean().shouldBe(false)
  }

  @Test
  fun `a second override of an already overridden key is ignored but reports a diagnostic`() {
    val pluginB = PluginBuilder.withModulesLang()
      .id("pluginB")
      .extensions(registryKey("my.key", defaultValue = "base"))
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = "1", overrides = true))
    val plugin2 = PluginBuilder.withModulesLang()
      .id("plugin2")
      .extensions(registryKey("my.key", defaultValue = "2", overrides = true))

    lateinit var registry: Map<String, RegistryKeyDescriptor>
    val message = executeAndReturnPluginLoadingWarning {
      registry = emulateStaticRegistryLoad(pluginB, plugin1, plugin2)
    }
    message.shouldBe("Incorrect registry key override for key my.key: both plugins plugin1 and plugin2 claim to override it to different defaults.")

    registry["my.key"].shouldNotBeNull().defaultValue.shouldBe("1")
  }

  @Test
  fun `a key should be deregistered after the owner plugin is unloaded`() {
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = "1"))

    val nestedDisposable = Disposer.newDisposable()
    try {
      loadPlugins(nestedDisposable, plugin1)
      val key = getContributedKeyDescriptor("my.key")
      key.shouldNotBeNull().pluginId.shouldNotBeNull().shouldBe("plugin1")
    } finally {
      Disposer.dispose(nestedDisposable)
    }

    getContributedKeyDescriptor("my.key").shouldBeNull()
  }

  @Test
  fun `a key should not be deregistered after the owner plugin wasn't actually owning it`() {
    val plugin1 = PluginBuilder.withModulesLang()
      .id("plugin1")
      .extensions(registryKey("my.key", defaultValue = "1"))
    val plugin2 = PluginBuilder.withModulesLang()
      .id("plugin2")
      .extensions(registryKey("my.key", defaultValue = "2", overrides = true))

    loadPlugins(testDisposable, plugin1)

    val nestedDisposable = Disposer.newDisposable()
    try {
      val message = executeAndReturnPluginLoadingWarning {
        loadPlugins(nestedDisposable, plugin2)
      }
      message.shouldStartWith("A dynamically-loaded plugin plugin2 is forbidden to override the registry key my.key introduced by plugin1.")

      val key = getContributedKeyDescriptor("my.key")
      key.shouldNotBeNull().pluginId.shouldNotBeNull().shouldBe("plugin1")
    } finally {
      Disposer.dispose(nestedDisposable)
    }

    getContributedKeyDescriptor("my.key").shouldNotBeNull()
      .pluginId.shouldNotBeNull().shouldBe("plugin1")
  }

  /**
   * "Static" means a Registry loading as if the plugins were loaded during the startup, i.e., not dynamically.
   */
  private fun emulateStaticRegistryLoad(vararg plugins: PluginBuilder): Map<String, RegistryKeyDescriptor> {
    val point = (ApplicationManager.getApplication().extensionArea)
      .getExtensionPoint<RegistryKeyBean>("com.intellij.registryKey")
    val beans = mutableListOf<Pair<RegistryKeyBean, PluginDescriptor>>()
    point.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean> {
      override fun extensionAdded(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
        beans.add(extension to pluginDescriptor)
      }
    }, false, testDisposable)

    LoggedErrorProcessor.executeWith<Nothing>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): MutableSet<Action> {
        if (category == RegistryKeyBean.KEY_CONFLICT_LOG_CATEGORY) return Action.NONE
        return super.processError(category, message, details, t)
      }
    }) {
      loadPlugins(testDisposable, *plugins)
    }

    val registryMock = mutableMapOf<String, RegistryKeyDescriptor>()
    for ((bean, pd) in beans) {
      val keyDescriptor = RegistryKeyBean.createRegistryKeyDescriptor(bean, pd)
      RegistryKeyBean.putNewDescriptorConsideringOverrides(registryMock, keyDescriptor, isDynamic = false)
    }
    return registryMock
  }

  private fun loadPlugins(disposable: Disposable, vararg plugins: PluginBuilder) {
    for (plugin in plugins) {
      val pluginDisposable = loadPluginWithText(plugin, rootPath.resolve(Ksuid.generate()))
      Disposer.register(disposable, pluginDisposable)
    }
  }
}

private fun registryKey(name: String, defaultValue: Any, overrides: Boolean = false) =
  """<registryKey key="$name" defaultValue="$defaultValue" ${if (overrides) "overrides=\"true\"" else ""} />"""

private fun getContributedKeyDescriptor(@Suppress("SameParameterValue") name: String): RegistryKeyDescriptor? {
  var descriptor: RegistryKeyDescriptor? = null
  Registry.mutateContributedKeys { keys ->
    descriptor = keys[name]
    keys
  }
  return descriptor
}

private fun executeAndReturnPluginLoadingWarning(block: () -> Unit): String {
  val error = AtomicReference<String?>(null)
  LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
    override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
      if (category == RegistryKeyBean.KEY_CONFLICT_LOG_CATEGORY) {
        error.compareAndSet(null, message).shouldBeTrue()
      }

      return super.processWarn(category, message, t)
    }
  }, block)

  return error.value.shouldNotBeNull()
}