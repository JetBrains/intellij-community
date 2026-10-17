// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.JBColor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(ExperimentalIcons.REGISTRY_KEY, "true")
class ActionButtonIconDescriptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun activateNewIconManager() {
      // only IDE bootstrap activates it, and only when not headless
      IntelliJIconManager.activate()
      IconLoader.activate()
    }

    @JvmStatic
    @AfterAll
    fun deactivateIcons() {
      IconLoader.deactivate()
    }
  }

  @Test
  fun `descriptor is rendered through the new pipeline`() {
    val button = button(executeDescriptor())
    val icon = assertIs<ScalableSwingIcon>(button.icon)
    assertEquals(16, icon.iconWidth)
    assertEquals(16, icon.iconHeight)
    assertTrue(icon.paintToImage().hasVisiblePixels(), "the new pipeline painted nothing")
  }

  @Test
  fun `missing descriptor paints the unknown marker`() {
    val button = button(null)
    val icon = assertIs<ScalableSwingIcon>(button.icon)
    assertEquals(16, icon.iconWidth)
    assertEquals(16, icon.iconHeight)
    assertEquals(unknownMarkerPixels(), icon.paintToImage().visiblePixels(), "the button did not paint the unknown marker")
  }

  @Test
  fun `selected state keeps the descriptor icon instead of the legacy hovered icon`() {
    val button = buttonWithLegacyStateIcons()
    val descriptorIcon = button.icon

    // a selected button is PUSHED, the state that used to pick the legacy hovered icon
    Toggleable.setSelected(button.presentation, true)

    assertSame(descriptorIcon, button.icon)
  }

  @Test
  fun `disabled state derives from the descriptor instead of the legacy disabled icon`() {
    val button = buttonWithLegacyStateIcons()
    val enabledIcon = button.icon

    button.presentation.isEnabled = false
    val disabledIcon = button.icon

    assertNotSame(AllIcons.General.Remove, disabledIcon)
    assertNotSame(enabledIcon, disabledIcon)
    assertTrue(disabledIcon.paintToImage().hasVisiblePixels(), "the disabled descriptor icon painted nothing")
  }

  @Test
  fun `disabled icon is gray and translucent`() {
    val button = button(executeDescriptor())
    button.presentation.isEnabled = false
    val disabled = assertIs<ScalableSwingIcon>(button.icon).paintToImage()
    val pixels = disabled.visiblePixels()
    val alpha = disabled.maxAlpha()

    assertTrue(pixels.isNotEmpty(), "the disabled icon painted nothing")
    assertTrue(alpha < 255, "the disabled icon is opaque: max alpha $alpha")
    assertTrue(pixels.all { isGray(it) }, "the disabled icon has colored pixels")
  }

  @Test
  fun `disabled icon is cached while the theme stays the same`() {
    val button = button(executeDescriptor())
    button.presentation.isEnabled = false
    val disabled = button.icon
    button.updateIcon()
    assertSame(disabled, button.icon)
  }

  @Test
  fun `disabled icon changes after a theme change`() {
    assertTrue(JBColor.isBright(), "the test must start on a bright theme")

    val button = button(executeDescriptor())
    button.presentation.isEnabled = false

    val brightIcon = button.icon
    val brightAlpha = brightIcon.paintToImage().maxAlpha()

    val (darkIcon, darkAlpha) =
      withDarkTheme {
        button.updateIcon()

        val icon = button.icon
        icon to icon.paintToImage().maxAlpha()
      }

    assertNotSame(brightIcon, darkIcon, "the disabled icon was not rebuilt after the theme change")
    assertTrue(darkAlpha < brightAlpha, "dark alpha $darkAlpha is not below bright alpha $brightAlpha")
  }

  @Test
  fun `enabled icon is rebuilt after a theme change`() {
    assertTrue(JBColor.isBright(), "the test must start on a bright theme")

    val button = button(executeDescriptor())

    val brightIcon = button.icon

    val darkIcon =
      withDarkTheme {
        button.updateIcon()

        button.icon
      }

    assertNotSame(brightIcon, darkIcon, "the enabled icon was not rebuilt after the theme change")
  }

  @Test
  fun `enabled button under the mouse returns the hovered icon`() {
    val button = button(executeDescriptor(), rollover = true)

    assertEquals(ActionButtonComponent.POPPED, button.popState, "button is not hovered")

    val base = assertIs<ScalableSwingIcon>(button.icon)

    button.presentation.hoveredIconDescriptor = closeDescriptor()

    val hovered = assertIs<ScalableSwingIcon>(button.icon)

    assertNotSame(base, hovered, "icons are the same")

    val basePainted = base.pixels()
    val hoveredPainted = hovered.pixels()

    assertTrue(basePainted.isNotEmpty(), "base icon painted nothing")
    assertTrue(hoveredPainted.isNotEmpty(), "hovered icon painted nothing")

    assertNotEquals(basePainted, hoveredPainted, "both base and hovered icons painted same pixels")
    assertEquals(closeDescriptor().pixels(), hoveredPainted, "hovered icon painted different pixels")
  }

  @Test
  fun `disabled button under the mouse ignores the hovered icon`() {
    val button = button(executeDescriptor(), rollover = true)

    assertEquals(ActionButtonComponent.POPPED, button.popState, "button is not hovered")

    button.presentation.hoveredIconDescriptor = closeDescriptor()

    val hovered = assertIs<ScalableSwingIcon>(button.icon)

    button.presentation.isEnabled = false

    val disabled = button.icon
    assertNotSame(hovered, disabled, "the disabled button still shows the hovered icon")

    val pixels = disabled.pixels()
    assertTrue(pixels.isNotEmpty(), "the disabled icon painted nothing")
    assertTrue(pixels.all { isGray(it) }, "the disabled button shows a colored icon, not the disabled one")
  }

  @Test
  fun `selected button returns the selected icon`() {
    val button = button(executeDescriptor())

    val base = assertIs<ScalableSwingIcon>(button.icon)

    button.presentation.selectedIconDescriptor = closeDescriptor()

    assertSame(base, button.icon, "button that is not selected shows the selected icon")

    Toggleable.setSelected(button.presentation, true)

    assertEquals(ActionButtonComponent.PUSHED, button.popState, "button is not selected")

    val selected = assertIs<ScalableSwingIcon>(button.icon)

    assertNotSame(base, selected, "icons are the same")

    val basePainted = base.pixels()
    val selectedPainted = selected.pixels()

    assertTrue(basePainted.isNotEmpty(), "base icon painted nothing")
    assertTrue(selectedPainted.isNotEmpty(), "selected icon painted nothing")

    assertNotEquals(basePainted, selectedPainted, "both base and selected icons painted same pixels")
    assertEquals(closeDescriptor().pixels(), selectedPainted, "selected icon painted different pixels")
  }

  @Test
  fun `selected button without a selected icon returns the hovered icon`() {
    val button = button(executeDescriptor())

    button.presentation.hoveredIconDescriptor = closeDescriptor()

    val base = assertIs<ScalableSwingIcon>(button.icon)

    // a selected button is PUSHED, the state that picks the hovered icon, the same as on the legacy path
    Toggleable.setSelected(button.presentation, true)

    assertEquals(ActionButtonComponent.PUSHED, button.popState, "button is not selected")

    val selected = assertIs<ScalableSwingIcon>(button.icon)

    assertNotSame(base, selected, "icons are the same")
    assertEquals(closeDescriptor().pixels(), selected.pixels(), "selected button painted different pixels than the hovered icon")
  }

  @Test
  fun `selected button under the mouse returns the selected icon instead of the hovered icon`() {
    val button = button(executeDescriptor(), rollover = true)

    assertEquals(ActionButtonComponent.POPPED, button.popState, "button is not hovered")

    button.presentation.hoveredIconDescriptor = closeDescriptor()
    button.presentation.selectedIconDescriptor = addDescriptor()

    val hovered = assertIs<ScalableSwingIcon>(button.icon)

    Toggleable.setSelected(button.presentation, true)

    assertEquals(ActionButtonComponent.PUSHED, button.popState, "button is not selected")

    val selected = assertIs<ScalableSwingIcon>(button.icon)

    assertNotSame(hovered, selected, "icons are the same")

    val hoveredPainted = hovered.pixels()
    val selectedPainted = selected.pixels()

    assertTrue(hoveredPainted.isNotEmpty(), "hovered icon painted nothing")
    assertTrue(selectedPainted.isNotEmpty(), "selected icon painted nothing")

    assertNotEquals(hoveredPainted, selectedPainted, "both hovered and selected icons painted same pixels")
    assertEquals(closeDescriptor().pixels(), hoveredPainted, "hovered icon painted different pixels")
    assertEquals(addDescriptor().pixels(), selectedPainted, "selected icon painted different pixels")
  }

  @Test
  fun `button under the mouse returns the new hovered icon without an icon update`() {
    val button = button(executeDescriptor(), rollover = true)

    assertEquals(ActionButtonComponent.POPPED, button.popState, "button is not hovered")

    button.presentation.hoveredIconDescriptor = closeDescriptor()

    val hovered = assertIs<ScalableSwingIcon>(button.icon)

    // no updateIcon() call: the setter fires no event, so the button must resolve the descriptor on each read
    button.presentation.hoveredIconDescriptor = addDescriptor()

    val newHovered = assertIs<ScalableSwingIcon>(button.icon)

    assertNotSame(hovered, newHovered, "icons are the same")

    val hoveredPainted = hovered.pixels()
    val newHoveredPainted = newHovered.pixels()

    assertTrue(hoveredPainted.isNotEmpty(), "hovered icon painted nothing")
    assertTrue(newHoveredPainted.isNotEmpty(), "new hovered icon painted nothing")

    assertNotEquals(hoveredPainted, newHoveredPainted, "both old and new hovered icons painted same pixels")
    assertEquals(addDescriptor().pixels(), newHoveredPainted, "new hovered icon painted different pixels")
  }

  @Test
  fun `hovered icon is reused between reads`() {
    val button = button(executeDescriptor(), rollover = true)

    assertEquals(ActionButtonComponent.POPPED, button.popState, "button is not hovered")

    button.presentation.hoveredIconDescriptor = closeDescriptor()

    val first = assertIs<ScalableSwingIcon>(button.icon)

    // without this check, the base icon would also be the same instance on both reads
    assertEquals(closeDescriptor().pixels(), first.pixels(), "the button did not show the hovered icon")
    assertSame(first, button.icon, "the hovered icon was rebuilt on the second read")
  }

  @Test
  fun `selected icon is reused between reads`() {
    val button = button(executeDescriptor())

    button.presentation.selectedIconDescriptor = closeDescriptor()
    Toggleable.setSelected(button.presentation, true)

    assertEquals(ActionButtonComponent.PUSHED, button.popState, "button is not selected")

    val first = assertIs<ScalableSwingIcon>(button.icon)

    // without this check, the base icon would also be the same instance on both reads
    assertEquals(closeDescriptor().pixels(), first.pixels(), "the button did not show the selected icon")
    assertSame(first, button.icon, "the selected icon was rebuilt on the second read")
  }

  @Test
  fun `popup without icons clears the base and hovered descriptors`() {
    val button = button(executeDescriptor())

    val popupAction = action(closeDescriptor())
    popupAction.templatePresentation.hoveredIconDescriptor = addDescriptor()
    popupAction.templatePresentation.selectedIconDescriptor = addDescriptor()

    val withIcons = button.popupPresentationFactory().getPresentation(popupAction)

    assertNotNull(withIcons.iconDescriptor, "popup with icons lost the base descriptor")
    assertNotNull(withIcons.hoveredIconDescriptor, "popup with icons lost the hovered descriptor")

    button.setNoIconsInPopup(true)

    val withoutIcons = button.popupPresentationFactory().getPresentation(popupAction)

    assertNull(withoutIcons.iconDescriptor, "popup without icons kept the base descriptor")
    assertNull(withoutIcons.hoveredIconDescriptor, "popup without icons kept the hovered descriptor")
    // the legacy path does not clear the selected icon, so the selected descriptor stays too
    assertNotNull(withoutIcons.selectedIconDescriptor, "popup without icons lost the selected descriptor")
  }

  private fun button(
    descriptor: IconDescriptor?,
    rollover: Boolean = false,
  ): ActionButton {
    val action = action(descriptor)
    val button =
      object : ActionButton(action, action.templatePresentation.clone(), "TestPlace", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        init {
          myRollover = rollover
        }
      }
    button.updateIcon()
    return button
  }

  private fun action(descriptor: IconDescriptor?): AnAction =
    object : AnAction({ "test" }, null, descriptor, null) {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }

  /** A descriptor-backed button whose presentation also carries legacy hovered and disabled icons. */
  private fun buttonWithLegacyStateIcons(): ActionButton {
    val button = button(executeDescriptor())
    button.presentation.hoveredIcon = AllIcons.General.Add
    button.presentation.disabledIcon = AllIcons.General.Remove
    return button
  }

  private fun executeDescriptor(): IconDescriptor = imageIconDescriptor("actions/execute.svg", AllIcons::class.java.classLoader)

  private fun closeDescriptor(): IconDescriptor = imageIconDescriptor("actions/close.svg", AllIcons::class.java.classLoader)

  private fun addDescriptor(): IconDescriptor = imageIconDescriptor("general/add.svg", AllIcons::class.java.classLoader)
}

/** The visible pixels of the icon painted at its own size. */
private fun Icon.pixels(): List<Int> = paintToImage().visiblePixels()

/** The visible pixels of a new icon built from the descriptor. */
private fun IconDescriptor.pixels(): List<Int> = createSwingIcon().pixels()

/** The presentation factory of the button's popup. It is private in [ActionButton], so the test reaches it by reflection. */
private fun ActionButton.popupPresentationFactory(): PresentationFactory {
  val method = ActionButton::class.java.getDeclaredMethod("createPresentationFactory")
  method.isAccessible = true
  return method.invoke(this) as PresentationFactory
}
