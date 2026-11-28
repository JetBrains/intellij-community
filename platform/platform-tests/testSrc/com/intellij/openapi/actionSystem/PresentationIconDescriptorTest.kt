// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.function.Supplier
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    // the icon follows the descriptor under the pipeline
    assertEquals(listOf(Presentation.PROP_ICON_DESCRIPTOR, Presentation.PROP_ICON), events)
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
  fun `an icon set while the flag was off gets an equal descriptor on each read, and the read keeps no state`() {
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add
    val registryValue = Registry.get(ExperimentalIconRendering.REGISTRY_KEY)
    val disposable = Disposer.newDisposable()
    try {
      registryValue.setValue(true, disposable)
      val first = presentation.iconDescriptor
      assertNotNull(first)
      assertEquals(first, presentation.iconDescriptor)
      assertNull(presentation.iconDescriptorSupplier, "a read must not set the descriptor")
    }
    finally {
      Disposer.dispose(disposable)
    }
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
  fun `flag on an action with an icon and no descriptor keeps the bridge of the icon`() {
    val action = object : AnAction({ "test" }, null, null, AllIcons.General.Add) {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }

    val bridged = Presentation().apply { icon = AllIcons.General.Add }.iconDescriptor
    assertNotNull(bridged)
    assertEquals(bridged, action.templatePresentation.iconDescriptor, "the bridge of the icon is the descriptor")
    assertNotNull(action.templatePresentation.iconDescriptorSupplier, "the constructor must not remove the bridge")
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on the bridged descriptor follows the supplier and converts each icon once`() {
    var evaluations = 0
    var current: Icon? = null
    val presentation = Presentation()
    presentation.setIconSupplier {
      evaluations++
      current
    }
    assertEquals(0, evaluations, "setting the supplier must not evaluate it")

    // a supplier that has no icon yet, like a live supplier of an action
    assertNull(presentation.iconDescriptor)

    current = AllIcons.General.Add
    val add = presentation.iconDescriptor
    assertNotNull(add)
    assertSame(add, presentation.iconDescriptor, "the same icon must not be converted again")

    current = AllIcons.General.Remove
    val remove = presentation.iconDescriptor
    assertNotNull(remove)
    assertNotEquals(add, remove, "the descriptor must follow the icon of the supplier")
    assertEquals(4, evaluations, "the supplier is read on each read of the descriptor, as getIcon reads it")
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on an icon set later replaces the descriptor, like an icon set in update`() {
    val presentation = Presentation()
    presentation.iconDescriptor = TestDescriptor()

    presentation.icon = AllIcons.General.Add
    val bridged = presentation.iconDescriptor
    assertNotNull(bridged)
    assertFalse(bridged is TestDescriptor, "the icon must replace the descriptor")

    presentation.setIconSupplier { AllIcons.General.Remove }
    assertNotEquals(bridged, presentation.iconDescriptor)

    presentation.icon = null
    assertNull(presentation.iconDescriptor, "removing the icon removes the descriptor")
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on an equal icon that replaces an explicit descriptor fires a descriptor event and an icon event`() {
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add
    presentation.iconDescriptor = TestDescriptor()
    val events = mutableListOf<String>()
    presentation.addPropertyChangeListener { events += it.propertyName }

    presentation.setIconSupplier { AllIcons.General.Add }

    assertFalse(presentation.iconDescriptor is TestDescriptor, "the icon must replace the descriptor")
    // the legacy icon is equal, but getIcon follows the descriptor, and the descriptor is not equal
    assertEquals(listOf(Presentation.PROP_ICON, Presentation.PROP_ICON_DESCRIPTOR), events)

    events.clear()
    presentation.iconDescriptor = TestDescriptor()
    events.clear()
    presentation.icon = AllIcons.General.Add
    assertEquals(listOf(Presentation.PROP_ICON, Presentation.PROP_ICON_DESCRIPTOR), events)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on setting the same icon again keeps the bridged descriptor`() {
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add
    val bridged = presentation.iconDescriptor

    presentation.icon = AllIcons.General.Add

    assertSame(bridged, presentation.iconDescriptor)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on setting the icon of the presentation back keeps its descriptor`() {
    val descriptor = imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
    val presentation = Presentation()
    presentation.iconDescriptor = descriptor

    // e.g. an update() that writes the read icon back, on every update
    repeat(3) { presentation.icon = presentation.icon }

    assertSame(descriptor, presentation.iconDescriptor)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on copyFrom an empty presentation clears the icon also for a listener that reads the descriptor`() {
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add
    presentation.iconDescriptor = TestDescriptor()
    val seen = mutableListOf<Pair<String, IconDescriptor?>>()
    // like ActionButton, which reads the descriptor in its property change listener
    presentation.addPropertyChangeListener { seen += it.propertyName to presentation.iconDescriptor }

    presentation.copyFrom(Presentation())

    assertNull(presentation.iconDescriptor, "no descriptor of the old icon may be left")
    assertNull(presentation.icon)
    assertTrue(seen.isNotEmpty(), "the listener must be notified")
    assertTrue(seen.all { it.second == null }, "the listener must not read a descriptor of the old icon: $seen")
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on copyFrom gives a listener the new icon and the new descriptor together`() {
    val newDescriptor = KeyDescriptor("new")
    val source = Presentation()
    source.icon = AllIcons.General.Remove
    source.iconDescriptor = newDescriptor
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add
    presentation.iconDescriptor = KeyDescriptor("old")
    val seen = mutableListOf<Pair<Icon?, IconDescriptor?>>()
    presentation.addPropertyChangeListener {
      if (it.propertyName == Presentation.PROP_ICON || it.propertyName == Presentation.PROP_ICON_DESCRIPTOR) {
        seen += presentation.iconSupplier?.get() to presentation.iconDescriptor
      }
    }

    presentation.copyFrom(source)

    assertEquals(listOf<Pair<Icon?, IconDescriptor?>>(AllIcons.General.Remove to newDescriptor, AllIcons.General.Remove to newDescriptor), seen)
    assertSame(newDescriptor, presentation.iconDescriptor)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on copyFrom copies the descriptor of the source, not a bridge of its icon`() {
    val descriptor = TestDescriptor()
    val template = Presentation.newTemplatePresentation()
    template.icon = AllIcons.General.Add
    template.iconDescriptor = descriptor
    val presentation = Presentation()

    presentation.copyFrom(template)

    assertSame(descriptor, presentation.iconDescriptor)
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
    presentation.iconDescriptor = imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)

    // the same Swing icon while the descriptor stays equal, so that AbstractButton.setIcon does not repaint
    assertSame(presentation.icon, presentation.icon)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on the icon of an equal descriptor is the same instance, and another descriptor gives another icon`() {
    val presentation = Presentation()
    presentation.iconDescriptor = imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
    val icon = presentation.icon
    assertNotNull(icon)

    presentation.iconDescriptor = imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
    assertSame(icon, presentation.icon)

    presentation.iconDescriptor = imageIconDescriptor("expui/general/remove.svg", AllIcons::class.java.classLoader)
    assertNotEquals(icon, presentation.icon)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on copyFrom reuses the Swing icon of the source for an equal descriptor`() {
    val source = Presentation()
    source.iconDescriptor = imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
    val icon = source.icon

    val target = Presentation()
    target.copyFrom(source)
    assertSame(icon, target.icon)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on getIcon returns the icon that setIcon set`() {
    val presentation = Presentation()
    val icon = AllIcons.General.Add
    presentation.icon = icon
    assertSame(icon, presentation.icon)
    assertSame(presentation.icon, presentation.icon)

    presentation.setIconSupplier { AllIcons.General.Remove }
    assertSame(AllIcons.General.Remove, presentation.icon)

    val copy = Presentation()
    copy.copyFrom(presentation)
    assertSame(AllIcons.General.Remove, copy.icon)
  }

  @Test
  fun `an icon set while the flag was off is returned as it is when the flag is on`() {
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add
    val disposable = Disposer.newDisposable()
    try {
      Registry.get(ExperimentalIconRendering.REGISTRY_KEY).setValue(true, disposable)
      assertSame(AllIcons.General.Add, presentation.icon)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on a null descriptor removes the icon also when a legacy icon is set, and the events match what listeners read`() {
    val presentation = Presentation()
    // e.g. an XML action with an icon attribute
    presentation.icon = AllIcons.General.Add
    val bridged = assertNotNull(presentation.iconDescriptor)
    val events = mutableListOf<Triple<String, Any?, Any?>>()
    val read = mutableListOf<Pair<IconDescriptor?, Icon?>>()
    presentation.addPropertyChangeListener {
      events += Triple(it.propertyName, it.oldValue, it.newValue)
      read += presentation.iconDescriptor to presentation.icon
    }

    presentation.iconDescriptor = null

    assertNull(presentation.iconDescriptor, "the legacy icon must not be bridged again")
    assertNull(presentation.icon)
    assertEquals(listOf(Presentation.PROP_ICON_DESCRIPTOR, Presentation.PROP_ICON), events.map { it.first })
    assertEquals(bridged, events[0].second)
    assertNull(events[0].third)
    assertNull(events[1].third)
    assertTrue(read.all { it == (null to null) }, "a listener must read the values of the event: $read")

    presentation.icon = AllIcons.General.Remove

    assertNotNull(presentation.iconDescriptor, "a new legacy icon is bridged")
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on a new descriptor also fires the icon event, because the icon follows the descriptor`() {
    val presentation = Presentation()
    presentation.iconDescriptor = imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
    val oldIcon = presentation.icon
    val events = mutableListOf<Triple<String, Any?, Any?>>()
    presentation.addPropertyChangeListener { events += Triple(it.propertyName, it.oldValue, it.newValue) }

    presentation.iconDescriptor = imageIconDescriptor("expui/general/remove.svg", AllIcons::class.java.classLoader)

    val iconEvent = events.single { it.first == Presentation.PROP_ICON }
    assertEquals(oldIcon, iconEvent.second)
    assertEquals(presentation.icon, iconEvent.third)
    assertNotEquals(iconEvent.second, iconEvent.third)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on copyFrom of a changed or cleared descriptor fires the icon event`() {
    val live = Presentation()
    live.icon = AllIcons.General.Add
    live.iconDescriptor = imageIconDescriptor("expui/general/add.svg", AllIcons::class.java.classLoader)
    val iconEvents = mutableListOf<Pair<Any?, Any?>>()
    live.addPropertyChangeListener { if (it.propertyName == Presentation.PROP_ICON) iconEvents += it.oldValue to it.newValue }

    // like ActionUpdater: update() changes only the descriptor of a copy, which is then copied back
    val changed = Presentation()
    changed.copyFrom(live)
    changed.iconDescriptor = imageIconDescriptor("expui/general/remove.svg", AllIcons::class.java.classLoader)
    val oldIcon = live.icon
    live.copyFrom(changed)

    assertEquals(listOf<Pair<Any?, Any?>>(oldIcon to live.icon), iconEvents)
    assertNotEquals(oldIcon, live.icon)

    iconEvents.clear()
    val cleared = Presentation()
    cleared.copyFrom(live)
    cleared.iconDescriptor = null
    val changedIcon = live.icon
    live.copyFrom(cleared)

    assertNull(live.icon, "the legacy icon must not be bridged again")
    assertEquals(listOf<Pair<Any?, Any?>>(changedIcon to null), iconEvents)
  }

  @Test
  @RegistryKey(ExperimentalIconRendering.REGISTRY_KEY, "true")
  fun `flag on the same legacy icon set over an explicit descriptor fires the icon event`() {
    val presentation = Presentation()
    presentation.icon = AllIcons.General.Add
    presentation.iconDescriptor = imageIconDescriptor("expui/general/remove.svg", AllIcons::class.java.classLoader)
    val oldIcon = presentation.icon
    val iconEvents = mutableListOf<Pair<Any?, Any?>>()
    presentation.addPropertyChangeListener { if (it.propertyName == Presentation.PROP_ICON) iconEvents += it.oldValue to it.newValue }

    // the legacy icon replaces the explicit descriptor, so the icon changes though the legacy icon stays the same
    presentation.icon = AllIcons.General.Add

    assertEquals(listOf<Pair<Any?, Any?>>(oldIcon to presentation.icon), iconEvents)
    assertNotEquals(oldIcon, presentation.icon)
  }
}
