// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.TestLoggerFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * A plugin must not be able to prevent the IDE from starting by declaring an extension point that is already declared - IJPL-251786.
 */
class DuplicateExtensionPointRegistrationTest {
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
  }

  private val componentManager = ExtensionPointImplTest.MyComponentManager()
  private val first = DefaultPluginDescriptor("first")
  private val second = DefaultPluginDescriptor("second")

  @Test
  fun `duplicate declaration is reported and the first declaration wins`() {
    val points = registerPointsOfFirstPlugin()

    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      registerPoints(points, second, "test.duplicated")
    }

    assertThat(error.message).contains("test.duplicated", "first", "second")
    assertThat(points["test.duplicated"]?.getPluginDescriptor()).isSameAs(first)
  }

  @Test
  fun `registration continues after a duplicate declaration`() {
    val points = registerPointsOfFirstPlugin()

    LoggedErrorProcessor.executeAndReturnLoggedError {
      registerPoints(points, second, "test.duplicated", "test.own")
    }

    assertThat(points["test.own"]?.getPluginDescriptor()).isSameAs(second)
  }

  private fun registerPointsOfFirstPlugin(): MutableMap<String, ExtensionPointImpl<*>> {
    val result = HashMap<String, ExtensionPointImpl<*>>()
    registerPoints(result, first, "test.duplicated")
    return result
  }

  private fun registerPoints(
    result: MutableMap<String, ExtensionPointImpl<*>>,
    pluginDescriptor: DefaultPluginDescriptor,
    vararg names: String,
  ) {
    createExtensionPoints(points = names.map(::point), componentManager = componentManager,
                          result = result, pluginDescriptor = pluginDescriptor)
  }

  private fun point(name: String): ExtensionPointDescriptor {
    return ExtensionPointDescriptor(name = name,
                                   isNameQualified = true,
                                   className = Runnable::class.java.name,
                                   isBean = false,
                                   hasAttributes = false,
                                   isDynamic = true)
  }
}
