// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@TestApplication
class PresentationIconDescriptorTest {
  private class TestDescriptor : com.intellij.platform.icons.Icon

  @Test
  fun `flag off returns the sentinel and leaves the legacy icon alone`() {
    val descriptor = TestDescriptor()
    val presentation = Presentation()
    presentation.iconDescriptor = descriptor

    assertSame(Presentation.NO_ICON_DESCRIPTOR, presentation.iconDescriptor)
    assertSame(descriptor, presentation.iconDescriptorSupplier?.get())
    assertNull(presentation.icon)
  }

  @Test
  @RegistryKey(ExperimentalIcons.REGISTRY_KEY, "true")
  fun `flag on returns the descriptor, copies it and fires an event`() {
    val descriptor = TestDescriptor()
    val template = Presentation.newTemplatePresentation()
    assertNull(template.iconDescriptor)
    template.iconDescriptor = descriptor
    assertSame(descriptor, template.iconDescriptor)

    val copy = Presentation()
    val events = mutableListOf<String>()
    copy.addPropertyChangeListener { events += it.propertyName }
    copy.copyFrom(template)

    assertSame(descriptor, copy.iconDescriptor)
    assertEquals(listOf(Presentation.PROP_ICON_DESCRIPTOR), events.filter { it == Presentation.PROP_ICON_DESCRIPTOR })
  }
}
