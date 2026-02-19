package com.intellij.settingsSync.core

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class MatrixIDEStartPluginManagerTest : BasePluginManagerTest() {
  companion object {

    @JvmStatic
    fun nonBundledParams(): Stream<Arguments> {
      val argumentsBuilder = Stream.builder<Arguments>()
      val tfn = arrayOf(true, false, null)
      for (ide in tfn) {
        for (json in tfn) {
          argumentsBuilder.add(Arguments.of(ide, json))
        }
      }
      return argumentsBuilder.build()
    }
    @JvmStatic
    fun bundledParams(): Stream<Arguments> {
      val argumentsBuilder = Stream.builder<Arguments>()
      for (ide in arrayOf(true, false)) {
        for (json in arrayOf(true, false, null)) {
          argumentsBuilder.add(Arguments.of(ide, json))
        }
      }
      return argumentsBuilder.build()
    }

    @JvmStatic
    fun essentialParams(): Stream<Arguments> {
      val argumentsBuilder = Stream.builder<Arguments>()
      for (json in arrayOf(true, false, null)) {
        argumentsBuilder.add(Arguments.of(true, json))
      }
      return argumentsBuilder.build()
    }
  }

  @ParameterizedTest(name = "ide, plugins.json:{0}, {1}")
  @MethodSource("nonBundledParams")
  fun `check non-bundled plugin`(ide: Boolean?, json: Boolean?) {
    val myPlugin = TestPluginDescriptor(
      "myPlugin",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = false
    )
    if (ide != null) {
      testPluginManager.addPluginDescriptors(myPlugin.withEnabled(ide))
    }
    pluginManager.updateStateFromIdeOnStart(state {
      if (json != null) {
        myPlugin(enabled = json)
      }
    })

    assertIdeState(state {
      if (ide != null) {
        myPlugin(enabled = ide)
      }
    })
    assertPluginManagerState(state {
      if (ide != null) {
        myPlugin(enabled = ide)
      }
      else if (json != null) { // ide == null
        myPlugin(enabled = false)
      }
    })
  }

  @ParameterizedTest(name = "ide, plugins.json:{0}, {1}")
  @MethodSource("bundledParams")
  fun `check bundled plugin`(ide: Boolean, json: Boolean?) {
    val myPlugin = TestPluginDescriptor(
      "myBundledPlugin",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = true
    )
    testPluginManager.addPluginDescriptors(myPlugin.withEnabled(ide))
    pluginManager.updateStateFromIdeOnStart(state {
      if (json != null) {
        myPlugin(enabled = json)
      }
    })
    assertIdeState(state {
      myPlugin(enabled = ide)
    })
    assertPluginManagerState(state {
      if (!ide) {
        myPlugin(enabled = false)
      }
    })
  }

  @ParameterizedTest(name = "ide, plugins.json:{0}, {1}")
  @MethodSource("essentialParams")
  fun `check essential plugin`(ide: Boolean?, json: Boolean?) {
    assert(ide!!)
    val myPlugin = TestPluginDescriptor(
      "myBundledPlugin",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = true, essential = true
    )
    testPluginManager.addPluginDescriptors(myPlugin.withEnabled(ide))
    pluginManager.updateStateFromIdeOnStart(state {
      if (json != null) {
        myPlugin(enabled = json)
      }
    })
    assertIdeState(state {
      myPlugin(enabled = true)
    })
    assertPluginManagerState(state {
      if (json != null) {
        myPlugin(enabled = json)
      }
    })
  }
}