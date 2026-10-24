// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.openapi.project.Project
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.design.px
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.filters.tint
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.intellij.testIcons
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.modifiers.DarkModifier
import com.intellij.platform.icons.impl.modifiers.ScaleModifier
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.modifiers.align
import com.intellij.platform.icons.modifiers.alpha
import com.intellij.platform.icons.modifiers.colorFilter
import com.intellij.platform.icons.modifiers.cutoutMargin
import com.intellij.platform.icons.modifiers.dark
import com.intellij.platform.icons.modifiers.margin
import com.intellij.platform.icons.modifiers.patchSvg
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.modifiers.sizeVariants
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.factor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.image.BufferedImage
import java.lang.reflect.Proxy
import javax.swing.JLabel

@TestApplication
class ModifierEditingTest {
  @AfterEach
  fun tearDown() {
    IconRenderDebugger.clearAll()
  }

  @Test
  fun `every modifier kind is described with typed fields`() = testIcons {
    val modifier = IconModifier.scale(factor(1.25)).margin(1.dp, 2.dp, 3.dp, 4.px).alpha(0.5f).align(IconAlign.TopRight)
      .colorFilter(ColorFilter.tint(Color.White)).stroke(Color.Black).cutoutMargin(1.5.dp).sizeVariants(SizeVariantPolicy.Fitting)
      .dark(false).patchSvg { }
    val described = ModifierEditing.describe(modifier)
    assertEquals(listOf(EditableModifierKind.SCALE, EditableModifierKind.MARGIN, EditableModifierKind.ALPHA, EditableModifierKind.ALIGN,
                        EditableModifierKind.COLOR_FILTER, EditableModifierKind.STROKE, EditableModifierKind.CUTOUT_MARGIN,
                        EditableModifierKind.SIZE_VARIANTS, EditableModifierKind.DARK, EditableModifierKind.READ_ONLY),
                 described.map { it.kind })
    assertEquals((0 until 10).map { "m$it" }, described.map { it.id })
    fun field(index: Int, key: String) = described[index].fields.single { it.key == key }
    assertEquals("1.25", field(0, "factor").value)
    assertEquals(listOf("1.0dp", "2.0dp", "3.0dp", "4px"), listOf("left", "top", "right", "bottom").map { field(1, it).value })
    assertEquals(ModifierFieldType.NUMBER, field(2, "alpha").type)
    assertEquals("Top", field(3, "vertical").value)
    assertEquals("Right", field(3, "horizontal").value)
    assertEquals("#FFFFFFFF", field(4, "color").value)
    assertEquals("#FF000000", field(5, "color").value)
    assertEquals("1.5dp", field(6, "size").value)
    assertEquals("Fitting", field(7, "policy").value)
    assertEquals("false", field(8, "dark").value)
    assertEquals(listOf("true", "false"), field(8, "dark").options)
    assertTrue(described[9].fields.none { it.editable })
  }

  @Test
  fun `the dark modifier is added and edited`() = testIcons {
    val original = iconDescriptor { image(testImage(16, 16)) }
    val address = listOf(LayerStep.Layer(0))
    fun darkStates(descriptor: IconDescriptor) =
      InspectorTreeBuilder.flattenModifiers(ModifierEditing.layerAt(descriptor, address)!!.modifier).map { (it as DarkModifier).dark }
    val added = ModifierEditing.apply(original, address, ModifierEdit.Add("n", EditableModifierKind.DARK)).getOrThrow()
    assertEquals(listOf(true), darkStates(added))
    val edited = ModifierEditing.apply(added, address, ModifierEdit.SetField("n", "m0", "dark", "false")).getOrThrow()
    assertEquals(listOf(false), darkStates(edited))
    assertNotNull(ModifierEditing.apply(added, address, ModifierEdit.SetField("n", "m0", "dark", "maybe")).exceptionOrNull())
  }

  @Test
  fun `setting the scale factor edits a copy and leaves the original unchanged`() = testIcons {
    val original = iconDescriptor { image(testImage(16, 16), IconModifier.scale(factor(1.0))) }
    val copy = ModifierEditing.apply(original, listOf(LayerStep.Layer(0)), ModifierEdit.SetField("n", "m0", "factor", "1.25")).getOrThrow()
    assertEquals(1.25, scaleOf(copy))
    assertEquals(1.0, scaleOf(original))
    assertNotSame(original, copy)
  }

  @Test
  fun `add, remove and move keep the application order`() = testIcons {
    val original = iconDescriptor { image(testImage(16, 16), IconModifier.alpha(0.5f)) }
    val address = listOf(LayerStep.Layer(0))
    val added = ModifierEditing.apply(original, address, ModifierEdit.Add("n", EditableModifierKind.SCALE)).getOrThrow()
    assertEquals(listOf(EditableModifierKind.ALPHA, EditableModifierKind.SCALE), kinds(added))
    val moved = ModifierEditing.apply(added, address, ModifierEdit.Move("n", "m1", 0)).getOrThrow()
    assertEquals(listOf(EditableModifierKind.SCALE, EditableModifierKind.ALPHA), kinds(moved))
    val removed = ModifierEditing.apply(moved, address, ModifierEdit.Remove("n", "m0")).getOrThrow()
    assertEquals(listOf(EditableModifierKind.ALPHA), kinds(removed))
    assertEquals(listOf(EditableModifierKind.ALPHA), kinds(original))
  }

  @Test
  fun `editing a nested layer rebuilds only the path to it`() = testIcons {
    val inner = iconDescriptor { image(testImage(16, 16)) }
    val sibling = ImageIconLayer(testImage(8, 8), IconModifier)
    val original = DefaultLayeredIconDescriptor(listOf(NestedIconLayer(inner, IconModifier), sibling))
    val address = listOf(LayerStep.Layer(0), LayerStep.Nested, LayerStep.Layer(0))
    val copy = ModifierEditing.apply(original, address, ModifierEdit.Add("n", EditableModifierKind.ALPHA)).getOrThrow()
      as DefaultLayeredIconDescriptor
    assertSame(sibling, copy.layers[1])
    val nested = copy.layers[0] as NestedIconLayer
    assertEquals(listOf(EditableModifierKind.ALPHA), ModifierEditing.describe(ModifierEditing.layerAt(copy, address)!!.modifier).map { it.kind })
    assertTrue(ModifierEditing.describe((inner as DefaultLayeredIconDescriptor).layers[0].modifier).isEmpty())
    assertNotSame(inner, nested.iconDescriptor)
  }

  @Test
  fun `invalid values are reported as errors`() = testIcons {
    val original = iconDescriptor {
      image(testImage(16, 16), IconModifier.scale(factor(1.0)).margin(1.dp).alpha(1f).stroke(Color.Black).patchSvg { })
    }
    val address = listOf(LayerStep.Layer(0))
    fun error(edit: ModifierEdit) = ModifierEditing.apply(original, address, edit).exceptionOrNull()?.message
    assertNotNull(error(ModifierEdit.SetField("n", "m0", "factor", "0")))
    assertNotNull(error(ModifierEdit.SetField("n", "m0", "factor", "abc")))
    assertTrue(error(ModifierEdit.SetField("n", "m1", "left", "2 inches"))!!.contains("dp"))
    assertNotNull(error(ModifierEdit.SetField("n", "m1", "left", "1.5px")))
    assertNotNull(error(ModifierEdit.SetField("n", "m2", "alpha", "2")))
    assertNotNull(error(ModifierEdit.SetField("n", "m3", "color", "#zz")))
    assertNotNull(error(ModifierEdit.SetField("n", "m4", "description", "x")))
    assertNotNull(error(ModifierEdit.Remove("n", "m9")))
    assertNotNull(error(ModifierEdit.Add("n", EditableModifierKind.READ_ONLY)))
    assertNull(error(ModifierEdit.SetField("n", "m1", "left", "3px")))
    assertNull(error(ModifierEdit.SetField("n", "m3", "color", "#80FF0000")))
  }

  @Test
  fun `controller edits, applies to a JLabel, detects replacement and reverts`() = testIcons {
    val scope = CoroutineScope(SupervisorJob())
    val controller = IconInspectorControllerImpl(unusedProject(), scope)
    try {
      val original = SwingIcon(iconDescriptor { image(testImage(16, 16)) })
      val label = runInEdtAndGet { JLabel(original).apply { setSize(32, 32) } }
      runInEdtAndWait { controller.inspect(PickedIcon(label, label, original, "JLabel", emptyList(), null, Point(4, 4)), true) }
      runInEdtAndWait { paint(original, label) }
      val captured = controller.awaitTarget { it.root.children.singleOrNull()?.debuggable == true }
      val layerNode = captured.root.children.single().children.first { it.kind == InspectorNodeKind.LAYER }
      assertTrue(layerNode.modifiers.isEmpty())
      assertEquals(true, controller.modifierEditing.value?.canApplyToComponent)

      assertNull(runInEdtAndGet { controller.editModifiers(ModifierEdit.Add(layerNode.id, EditableModifierKind.SCALE)) })
      assertNull(runInEdtAndGet { controller.editModifiers(ModifierEdit.SetField(layerNode.id, "m0", "factor", "2")) })
      assertNotNull(runInEdtAndGet { controller.editModifiers(ModifierEdit.SetField(layerNode.id, "m0", "factor", "-1")) })
      val edited = controller.awaitTarget { target -> target.summary.any { it.name.startsWith("Edited copy (2 edits)") } }
      val editedLayer = edited.root.children.single().children.first { it.id == layerNode.id }
      assertEquals("2.0", editedLayer.modifiers.single().fields.single { it.key == "factor" }.value)
      assertTrue(edited.root.children.single().debuggable.not())
      assertSame(original, label.icon)

      runInEdtAndWait { controller.setAppliedToComponent(true) }
      val installed = label.icon as SwingIcon
      assertNotSame(original, installed)
      assertEquals(2.0, scaleOf(installed.iconDescriptor))
      assertEquals(true, controller.modifierEditing.value?.appliedToComponent)

      // the component replaces its icon (like ActionButton.updateIcon on a presentation refresh)
      runInEdtAndWait { label.icon = SwingIcon(iconDescriptor { image(testImage(16, 16)) }) }
      runBlocking { withTimeout(10_000) { controller.modifierEditing.first { it?.message != null } } }
      assertEquals(2.0, scaleOf((label.icon as SwingIcon).iconDescriptor))

      runInEdtAndWait { controller.revertModifierEdits() }
      assertTrue(label.icon is SwingIcon && scaleOf((label.icon as SwingIcon).iconDescriptor) == null)
      assertEquals(0, controller.modifierEditing.value?.editCount)
    }
    finally {
      runInEdtAndWait { controller.dispose() }
      scope.cancel()
    }
  }

  private fun scaleOf(descriptor: IconDescriptor): Double? {
    val layer = (descriptor as DefaultLayeredIconDescriptor).layers[0]
    val scale = InspectorTreeBuilder.flattenModifiers(layer.modifier).filterIsInstance<ScaleModifier>().singleOrNull() ?: return null
    return (scale.scale as FactorScale).factor
  }

  private fun kinds(descriptor: IconDescriptor) =
    ModifierEditing.describe((descriptor as DefaultLayeredIconDescriptor).layers[0].modifier).map { it.kind }

  private fun paint(icon: SwingIcon, component: JLabel) {
    val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      icon.paintIcon(component, graphics, 0, 0)
    }
    finally {
      graphics.dispose()
    }
  }

  private fun unusedProject(): Project =
    Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, _, _ -> null } as Project

  private fun IconInspectorControllerImpl.awaitTarget(predicate: (InspectionTarget) -> Boolean): InspectionTarget = runBlocking {
    withTimeout(10_000) {
      (state.first { it is InspectorState.Inspecting && predicate(it.target) } as InspectorState.Inspecting).target
    }
  }
}
