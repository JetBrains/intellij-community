// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.newEditor.SettingsNewBadgeState
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

/**
 * The settings tree checks [SettingsNewBadgeState.hasNewOptions] on the EDT for every node it paints.
 * The method must answer from the extension point, and it must construct no configurable.
 *
 * A composite is not expanded here. The settings tree holds a node for every child, so it rolls the badge up
 * over its own nodes. An expansion of a `dynamic` composite would build every child of it on the EDT.
 *
 * The tree here is built from [ConfigurableWrapper] instances over lazy [ConfigurableEP] declarations.
 * Each test configurable records its own initialization in [InitLog].
 */
@TestApplication
internal class ConfigurableWrapperLazyInitTest {

  private val newBadgeState = SettingsNewBadgeState()

  private val pluginDescriptor = DefaultPluginDescriptor(
    PluginId.getId("com.intellij.openapi.options.configurableWrapperLazyInitTest"),
    ConfigurableWrapperLazyInitTest::class.java.classLoader,
  )

  @BeforeEach
  fun clearInitLog() {
    InitLog.clear()
  }

  @Test
  fun `a tree without a new options attribute stays uninitialized`() {
    val root = wrap(instanceEp("root", PlainConfigurable::class.java, listOf(
      instanceEp("group", PlainConfigurable::class.java, listOf(
        instanceEp("leaf", PlainConfigurable::class.java),
      )),
      instanceEp("sibling", PlainConfigurable::class.java),
    )))

    assertThat(newBadgeState.hasNewOptions(root)).isFalse()
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a declared attribute answers with no class at all`() {
    val ep = instanceEp("new leaf", PlainConfigurable::class.java)
    ep.newOptions = true

    assertThat(newBadgeState.hasNewOptions(wrap(ep))).isTrue()
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a composite is not expanded to answer the badge`() {
    val newLeaf = instanceEp("new leaf", PlainConfigurable::class.java)
    newLeaf.newOptions = true
    val root = wrap(instanceEp("root", PlainConfigurable::class.java, listOf(newLeaf)))

    // the settings tree rolls the badge up over its nodes, so the composite answers for itself only
    assertThat(newBadgeState.hasNewOptions(root)).isFalse()
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a provider declaration stays uninitialized`() {
    val root = wrap(providerEp("provided leaf", UntypedProvider::class.java))

    // a provider declaration answers from its own attribute, and this one declares none
    assertThat(newBadgeState.hasNewOptions(root)).isFalse()
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a declared promo icon answers with no construction`() {
    val ep = providerEp("promo leaf", UntypedProvider::class.java)
    ep.promoIcon = "AllIcons.Ultimate.Lock"

    // a provider declaration names no page class, so the attribute is the only answer the settings tree has
    assertThat(ep.lazyPromoIcon.value).isSameAs(AllIcons.Ultimate.Lock)
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a declaration without a promo icon answers null`() {
    val ep = providerEp("plain leaf", UntypedProvider::class.java)

    assertThat(ep.lazyPromoIcon.value).isNull()
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `preferred focus query leaves the configurable uninitialized`() {
    val configurable = wrap(instanceEp("focused", PreferredFocusConfigurable::class.java))

    assertThat(configurable.preferredFocusedComponent).isNull()
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a search by the declared class constructs nothing`() {
    val target = wrap(instanceEp("target", PlainConfigurable::class.java))
    val groups = listOf(groupOf(wrap(instanceEp("other", PreferredFocusConfigurable::class.java)), target))

    assertThat(ConfigurableVisitor.findByType(PlainConfigurable::class.java, groups)).isSameAs(target)
    assertThat(InitLog.initialized()).isEmpty()
  }

  @Test
  fun `a search by a base class still finds the page`() {
    val target = wrap(instanceEp("target", PlainConfigurable::class.java))
    val groups = listOf(groupOf(target))

    // no declaration names the base class, so the fallback walk answers, and it constructs the page
    assertThat(ConfigurableVisitor.findByType(TrackedConfigurable::class.java, groups)).isSameAs(target)
    assertThat(InitLog.initialized()).containsExactly(PlainConfigurable::class.java)
  }

  private fun wrap(ep: ConfigurableEP<Configurable>): Configurable {
    return requireNotNull(ConfigurableWrapper.wrapConfigurable(ep)) { "no wrapper for $ep" }
  }

  private fun groupOf(vararg configurables: Configurable): ConfigurableGroup {
    return object : ConfigurableGroup {
      override fun getDisplayName(): String = "test group"

      override fun getConfigurables(): Array<Configurable> = arrayOf(*configurables)
    }
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

  private class PreferredFocusConfigurable : TrackedConfigurable() {
    override fun getPreferredFocusedComponent(): JComponent = error("The wrapper must not call this method")
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
