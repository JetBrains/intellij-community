// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.platform.icons.IconDescriptor
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

@TestApplication
class PresentationIconDescriptorTest {
  private class TestDescriptor : IconDescriptor

  private data class KeyDescriptor(val key: String) : IconDescriptor

  @Test
  fun `flag off leaves the legacy icon alone`() {
    val descriptor = TestDescriptor()
    val presentation = Presentation()
    presentation.iconDescriptor = descriptor

    assertSame(Presentation.NO_ICON_DESCRIPTOR, presentation.iconDescriptor)
    assertSame(descriptor, presentation.iconDescriptorSupplier?.get())
    assertNull(presentation.icon)
  }

  @Test
  fun `flag off stores the supplier without evaluating it or firing an event`() {
    var evaluations = 0
    val supplier = Supplier<IconDescriptor> {
      evaluations++
      TestDescriptor()
    }
    val template = Presentation.newTemplatePresentation()
    template.iconDescriptorSupplier = supplier

    val presentation = Presentation()
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }
    presentation.copyFrom(template)

    assertEquals(0, evaluations)
    assertFalse(Presentation.PROP_ICON_DESCRIPTOR in events)
    assertSame(supplier, presentation.iconDescriptorSupplier)
  }

  @Test
  @RegistryKey(ExperimentalIcons.REGISTRY_KEY, "true")
  fun `flag on returns the descriptor, copies it and fires an event`() {
    val descriptor = TestDescriptor()
    val template = Presentation.newTemplatePresentation()
    assertNull(template.iconDescriptor)
    template.iconDescriptor = descriptor
    assertSame(descriptor, template.iconDescriptor)

    val presentation = Presentation()
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }
    presentation.copyFrom(template)

    assertSame(descriptor, presentation.iconDescriptor)
    assertEquals(listOf(Presentation.PROP_ICON_DESCRIPTOR), events.filter { it == Presentation.PROP_ICON_DESCRIPTOR })
  }

  @Test
  @RegistryKey(ExperimentalIcons.REGISTRY_KEY, "true")
  fun `flag on fires no event for an equal descriptor`() {
    val presentation = Presentation()
    presentation.iconDescriptor = KeyDescriptor("a")
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }

    presentation.iconDescriptor = KeyDescriptor("a")
    assertFalse(Presentation.PROP_ICON_DESCRIPTOR in events)

    presentation.iconDescriptor = KeyDescriptor("b")
    assertEquals(listOf(Presentation.PROP_ICON_DESCRIPTOR), events)
  }
}
