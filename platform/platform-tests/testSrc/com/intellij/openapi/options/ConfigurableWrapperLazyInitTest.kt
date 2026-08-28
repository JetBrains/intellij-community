// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.newEditor.SettingsTreeView
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

/**
 * The Settings tree calls [SettingsTreeView.hasNewOptions] on the EDT for every node it paints.
 * The method must read the [Configurable.NewOptions] marker from the extension point, and it must
 * keep every configurable without that marker uninitialized.
 *
 * The tree here is built from [ConfigurableWrapper] instances over lazy [ConfigurableEP] declarations.
 * Each test configurable records its own initialization in [InitLog].
 */
@TestApplication
internal class ConfigurableWrapperLazyInitTest {

  private val pluginDescriptor = DefaultPluginDescriptor(
    PluginId.getId("com.intellij.openapi.options.configurableWrapperLazyInitTest"),
    ConfigurableWrapperLazyInitTest::class.java.classLoader,
  )

  @BeforeEach
  fun clearInitLog() {
    InitLog.clear()
  }

  @Test
  fun `a tree without a new options marker stays uninitialized`() {
    val root = wrap(instanceEp("root", PlainConfigurable::class.java, listOf(
      instanceEp("group", PlainConfigurable::class.java, listOf(
        instanceEp("leaf", PlainConfigurable::class.java),
      )),
      instanceEp("sibling", PlainConfigurable::class.java),
    )))

    assertThat(SettingsTreeView.hasNewOptions(root)).isFalse()
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a nested new options marker leaves the other nodes uninitialized`() {
    val root = wrap(instanceEp("root", PlainConfigurable::class.java, listOf(
      instanceEp("plain group", PlainConfigurable::class.java, listOf(
        instanceEp("plain leaf", PlainConfigurable::class.java),
      )),
      instanceEp("new group", PlainConfigurable::class.java, listOf(
        instanceEp("new leaf", NewOptionsConfigurable::class.java),
      )),
    )))

    assertThat(SettingsTreeView.hasNewOptions(root)).isTrue()
    // only the marked leaf is initialized, and only because the current implementation casts it
    assertThat(InitLog.initialized()).containsExactly(NewOptionsConfigurable::class.java)
  }

  @Test
  fun `a provider that declares its configurable type stays uninitialized`() {
    val root = wrap(instanceEp("root", PlainConfigurable::class.java, listOf(
      providerEp("provided leaf", TypedProvider::class.java),
    )))

    assertThat(SettingsTreeView.hasNewOptions(root)).isFalse()
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a provider without a configurable type gets initialized`() {
    val root = wrap(instanceEp("root", PlainConfigurable::class.java, listOf(
      providerEp("provided leaf", UntypedProvider::class.java),
    )))

    // without the type hint the wrapper must create the configurable to answer the cast
    assertThat(SettingsTreeView.hasNewOptions(root)).isFalse()
    assertThat(InitLog.initialized()).containsExactly(PlainConfigurable::class.java)
  }

  private fun wrap(ep: ConfigurableEP<Configurable>): Configurable {
    return requireNotNull(ConfigurableWrapper.wrapConfigurable(ep)) { "no wrapper for $ep" }
  }

  private fun instanceEp(
    name: String,
    configurableClass: Class<out Configurable>,
    children: List<ConfigurableEP<*>> = emptyList(),
  ): ConfigurableEP<Configurable> {
    val ep = newEp(name, children)
    ep.instanceClass = configurableClass.name
    return ep
  }

  private fun providerEp(
    name: String,
    providerClass: Class<out ConfigurableProvider>,
    children: List<ConfigurableEP<*>> = emptyList(),
  ): ConfigurableEP<Configurable> {
    val ep = newEp(name, children)
    ep.providerClass = providerClass.name
    return ep
  }

  private fun newEp(name: String, children: List<ConfigurableEP<*>>): ConfigurableEP<Configurable> {
    val ep = ConfigurableEP<Configurable>(pluginDescriptor)
    ep.id = name
    // the display name keeps the wrapper lazy, see ConfigurableWrapper.wrapConfigurable
    ep.displayName = name
    if (children.isNotEmpty()) {
      ep.children = children.toMutableList()
    }
    return ep
  }

  private abstract class TrackedConfigurable : Configurable {
    init {
      InitLog.record(javaClass)
    }

    override fun getDisplayName(): String = javaClass.simpleName

    override fun createComponent(): JComponent? = null

    override fun isModified(): Boolean = false

    override fun apply() {
    }
  }

  private class PlainConfigurable : TrackedConfigurable()

  private class NewOptionsConfigurable : TrackedConfigurable(), Configurable.NewOptions

  private class TypedProvider : ConfigurableProvider() {
    override fun createConfigurable(): Configurable = PlainConfigurable()

    override fun getConfigurableType(): Class<*> = PlainConfigurable::class.java
  }

  private class UntypedProvider : ConfigurableProvider() {
    override fun createConfigurable(): Configurable = PlainConfigurable()
  }
}

private object InitLog {
  private val initialized = CopyOnWriteArrayList<Class<*>>()

  fun record(configurableClass: Class<*>) {
    initialized.add(configurableClass)
  }

  fun clear() {
    initialized.clear()
  }

  fun initialized(): List<Class<*>> = initialized.toList()
}
