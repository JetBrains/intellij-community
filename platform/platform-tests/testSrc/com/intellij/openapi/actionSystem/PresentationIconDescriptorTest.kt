// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageResIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
class PresentationIconDescriptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // bridging a legacy icon needs the icons API; only IDE bootstrap activates it, and only when not headless
      IntelliJIconManager.activate()
    }
  }

  private class TestDescriptor : IconDescriptor

  private data class KeyDescriptor(val key: String) : IconDescriptor

  @Test
  fun `flag off leaves the legacy icon alone`() {
    val descriptor = TestDescriptor()
    val presentation = Presentation()
    presentation.iconDescriptor = descriptor

    assertNull(presentation.iconDescriptor)
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
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
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
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
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

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on fires an event for a hovered and a selected descriptor`() {
    val hovered = TestDescriptor()
    val selected = TestDescriptor()
    val presentation = Presentation()
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }

    presentation.hoveredIconDescriptor = hovered
    presentation.selectedIconDescriptor = selected

    assertSame(hovered, presentation.hoveredIconDescriptor)
    assertSame(selected, presentation.selectedIconDescriptor)
    assertEquals(listOf(Presentation.PROP_HOVERED_ICON_DESCRIPTOR, Presentation.PROP_SELECTED_ICON_DESCRIPTOR), events)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on fires no event for an equal hovered or selected descriptor`() {
    val presentation = Presentation()
    presentation.hoveredIconDescriptor = KeyDescriptor("a")
    presentation.selectedIconDescriptor = KeyDescriptor("a")
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }

    presentation.hoveredIconDescriptor = KeyDescriptor("a")
    presentation.selectedIconDescriptor = KeyDescriptor("a")
    assertEquals(emptyList(), events)

    presentation.hoveredIconDescriptor = KeyDescriptor("b")
    presentation.selectedIconDescriptor = KeyDescriptor("b")
    assertEquals(listOf(Presentation.PROP_HOVERED_ICON_DESCRIPTOR, Presentation.PROP_SELECTED_ICON_DESCRIPTOR), events)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on fires an event when a hovered or selected descriptor is removed`() {
    val presentation = Presentation()
    presentation.hoveredIconDescriptor = TestDescriptor()
    presentation.selectedIconDescriptor = TestDescriptor()
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }

    presentation.hoveredIconDescriptor = null
    presentation.selectedIconDescriptor = null

    assertNull(presentation.hoveredIconDescriptor)
    assertNull(presentation.selectedIconDescriptor)
    assertEquals(listOf(Presentation.PROP_HOVERED_ICON_DESCRIPTOR, Presentation.PROP_SELECTED_ICON_DESCRIPTOR), events)
  }

  @Test
  fun `flag off fires no event for a hovered or selected descriptor`() {
    val hovered = TestDescriptor()
    val selected = TestDescriptor()
    val presentation = Presentation()
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }

    presentation.hoveredIconDescriptor = hovered
    presentation.selectedIconDescriptor = selected

    assertSame(hovered, presentation.hoveredIconDescriptor)
    assertSame(selected, presentation.selectedIconDescriptor)
    assertEquals(emptyList(), events)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `copyFrom fires events for the hovered and selected descriptors`() {
    val template = Presentation.newTemplatePresentation()
    template.hoveredIconDescriptor = TestDescriptor()
    template.selectedIconDescriptor = TestDescriptor()

    val presentation = Presentation()
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }
    presentation.copyFrom(template)

    val descriptorEvents = events.filter {
      it == Presentation.PROP_HOVERED_ICON_DESCRIPTOR || it == Presentation.PROP_SELECTED_ICON_DESCRIPTOR
    }
    assertEquals(2, descriptorEvents.size)
    assertEquals(
      setOf(Presentation.PROP_HOVERED_ICON_DESCRIPTOR, Presentation.PROP_SELECTED_ICON_DESCRIPTOR),
      descriptorEvents.toSet(),
    )
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `copyFrom copies the hovered and selected descriptors`() {
    val hovered = TestDescriptor()
    val selected = TestDescriptor()
    val template = Presentation.newTemplatePresentation()
    template.hoveredIconDescriptor = hovered
    template.selectedIconDescriptor = selected

    val presentation = Presentation()
    presentation.copyFrom(template)

    assertSame(hovered, presentation.hoveredIconDescriptor)
    assertSame(selected, presentation.selectedIconDescriptor)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `copyFrom clears variants the source does not have`() {
    val presentation = Presentation()
    presentation.hoveredIconDescriptor = TestDescriptor()
    presentation.selectedIconDescriptor = TestDescriptor()

    presentation.copyFrom(Presentation.newTemplatePresentation())

    assertNull(presentation.hoveredIconDescriptor)
    assertNull(presentation.selectedIconDescriptor)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `clone keeps the hovered and selected descriptors`() {
    val hovered = TestDescriptor()
    val selected = TestDescriptor()
    val template = Presentation.newTemplatePresentation()
    template.hoveredIconDescriptor = hovered
    template.selectedIconDescriptor = selected

    val clone = template.clone()

    assertSame(hovered, clone.hoveredIconDescriptor)
    assertSame(selected, clone.selectedIconDescriptor)
  }

  @Test
  fun `flag off setting an icon creates no descriptor`() {
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add
    presentation.setIconSupplier { AllIcons.General.Remove }

    assertNull(presentation.iconDescriptorSupplier)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on a presentation without an icon has neither icon nor descriptor`() {
    val presentation = Presentation()
    presentation.icon = null

    assertNull(presentation.iconDescriptor)
    assertNull(presentation.icon)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on an explicit descriptor survives a later icon`() {
    val descriptor = TestDescriptor()
    val action = object : AnAction({ "test" }, null, descriptor, AllIcons.General.Add) {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }

    assertSame(descriptor, action.templatePresentation.iconDescriptor)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on the bridged descriptor is converted lazily and once`() {
    var evaluations = 0
    val presentation = Presentation()
    presentation.setIconSupplier {
      evaluations++
      AllIcons.General.Add
    }
    assertEquals(0, evaluations, "setting the supplier must not evaluate it")

    val first = presentation.iconDescriptor
    assertNotNull(first)
    assertSame(first, presentation.iconDescriptor)
    assertEquals(1, evaluations)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on copyFrom keeps the bridge and fires no descriptor event for the same icon`() {
    val template = Presentation.newTemplatePresentation()
    template.setIconSupplier { AllIcons.General.Add }
    val presentation = Presentation()
    presentation.copyFrom(template)
    val bridged = presentation.iconDescriptor

    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }
    presentation.copyFrom(template)

    // (the legacy icon supplier itself is evaluated by setIconSupplier's own PROP_ICON change check, with listeners)
    assertSame(bridged, presentation.iconDescriptor)
    assertFalse(Presentation.PROP_ICON_DESCRIPTOR in events)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on the icon of a descriptor is equal across calls`() {
    val presentation = Presentation()
    presentation.iconDescriptor = imageResIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)

    // a new Swing icon per call, equal so that callers can compare them (e.g. CustomActionsSchema's original icon)
    assertEquals(presentation.icon, presentation.icon)
  }
}
