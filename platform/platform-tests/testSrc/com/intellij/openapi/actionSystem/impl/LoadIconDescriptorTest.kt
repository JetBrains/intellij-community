// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.icons.AllIconDescriptors
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [loadIconDescriptor] keeps the behavior of the legacy `loadIcon`: an icon reference of an XML action that cannot be
 * resolved is reported, and the action gets the unknown icon. So a usage site can tell a wrong icon declaration from an
 * action without an icon.
 */
@TestApplication
class LoadIconDescriptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      IntelliJIconManager.activate()
    }
  }

  private val corePlugin = assertNotNull(PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID))

  @Test
  fun `a reflective reference that cannot be resolved is reported and gives the unknown icon`() {
    lateinit var descriptor: Any
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      descriptor = loadIconDescriptor(module = corePlugin, iconPath = "AllIcons.Actions.NoSuchIcon", requestor = "TestAction")
    }

    assertSame(AllIconDescriptors.Nodes.Unknown, descriptor)
    assertTrue("AllIcons.Actions.NoSuchIcon" in error.message.orEmpty(), "the error must name the icon, was: ${error.message}")
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `a broken icon reference of an XML action gives one error, also when both suppliers are read`() {
    val errors = mutableListOf<String>()
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        errors += t?.message ?: message
        return Action.NONE
      }
    }
    LoggedErrorProcessor.executeWith<Throwable>(processor) {
      val presentation = Presentation()
      setXmlIconSuppliers(presentation, module = corePlugin, iconPath = "AllIcons.Actions.NoSuchIcon", requestor = "TestAction")
      assertSame(AllIconDescriptors.Nodes.Unknown, presentation.iconDescriptor)
      assertNotNull(presentation.icon)
      assertSame(AllIcons.Nodes.Unknown, presentation.iconSupplier?.get())
    }

    assertEquals(1, errors.size, "one error for one broken reference, was: $errors")
    assertTrue("AllIcons.Actions.NoSuchIcon" in errors.single(), errors.single())
  }

  @Test
  fun `an exception while the descriptor is resolved is reported once, with the exception as the cause`() {
    val causes = mutableListOf<Throwable?>()
    val warnings = mutableListOf<String>()
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        causes += t?.cause
        return Action.NONE
      }

      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        warnings += message
        return false
      }
    }
    val module = object : PluginDescriptor by corePlugin {
      override fun getClassLoader(): ClassLoader = object : ClassLoader(null) {
        override fun loadClass(name: String): Class<*> = throw IllegalStateException("broken class loader")
      }
    }
    LoggedErrorProcessor.executeWith<Throwable>(processor) {
      assertSame(AllIconDescriptors.Nodes.Unknown, loadIconDescriptor(module = module, iconPath = "com.example.SomeIcons.Bar", requestor = "TestAction"))
    }

    assertEquals(emptyList(), warnings)
    assertEquals(1, causes.size, "one error, was: $causes")
    assertTrue(causes.single() is IllegalStateException, "the error must carry the exception, was: ${causes.single()}")
  }

  @Test
  fun `a reflective reference that can be resolved gives its generated descriptor`() {
    val descriptor = loadIconDescriptor(module = corePlugin, iconPath = "AllIcons.Actions.Copy", requestor = "TestAction")

    assertSame(AllIconDescriptors.Actions.Copy, descriptor)
  }
}
