// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ExtensionPointCleanupTest {
  private val pluginDescriptor = DefaultPluginDescriptor("test")
  private val componentManager = ExtensionPointImplTest.MyComponentManager()

  @Test
  fun `remove unloaded extensions without creating them`() {
    val point = createPoint("missing.Extension")

    point.unregisterEverything(checkNotInstantiated = true)

    assertThat(point.size()).isZero()
  }

  @Test
  fun `reject removal of an instantiated extension`() {
    val point = createPoint(String::class.java.name)
    val extension = point.extensionList.single()

    assertThatThrownBy {
      point.unregisterExtensionsByClassName(String::class.java.name, checkNotInstantiated = true)
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(String::class.java.name)
      .hasMessageContaining(point.name)

    assertThat(point.size()).isEqualTo(1)
    assertThat(point.extensionList.single()).isSameAs(extension)
  }

  @Test
  fun `keep instantiated extensions outside the removal set`() {
    val point = createPoint(String::class.java.name, StringBuilder::class.java.name)
    val extension = point.sortedAdapters.first().createInstance<Any>(componentManager)

    point.unregisterExtensionsById(StringBuilder::class.java.name, checkNotInstantiated = true)

    assertThat(point.extensionList.single()).isSameAs(extension)
  }

  @Test
  fun `allow the filter to create a bean for inspection`() {
    val point = createPoint(String::class.java.name)

    point.unregisterExtensionsMatching(checkNotInstantiated = true) { _, adapter ->
      adapter.createInstance<Any>(componentManager) is String
    }

    assertThat(point.size()).isZero()
  }

  @Test
  fun `allow removal of instantiated extensions by default`() {
    val point = createPoint(String::class.java.name)
    assertThat(point.extensionList).hasSize(1)

    point.unregisterEverything()

    assertThat(point.size()).isZero()
  }

  private fun createPoint(vararg classNames: String): ExtensionPointImpl<*> {
    val points = HashMap<String, ExtensionPointImpl<*>>()
    val descriptor = ExtensionPointDescriptor(name = "test.cleanup",
                                              isNameQualified = true,
                                              className = Any::class.java.name,
                                              isBean = false,
                                              hasAttributes = false,
                                              isDynamic = true)
    createExtensionPoints(listOf(descriptor), componentManager, points, pluginDescriptor)
    return points.getValue(descriptor.name).also { point ->
      point.registerExtensions(classNames.map {
        ExtensionDescriptor(implementation = it, os = null, orderId = it, order = LoadingOrder.ANY,
                            element = null, hasExtraAttributes = false)
      }, pluginDescriptor, null)
    }
  }
}
