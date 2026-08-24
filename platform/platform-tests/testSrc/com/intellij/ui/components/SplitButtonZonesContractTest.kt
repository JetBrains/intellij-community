// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaOptionButtonUI
import com.intellij.ide.ui.laf.darcula.ui.ToolbarSplitButtonUI
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.SplitButtonAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.DefaultToolbarSplitButtonModel
import com.intellij.openapi.wm.impl.ToolbarSplitButton
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.LayoutManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Every platform split-button family, held to the one contract in [SplitButtonZones].
 *
 * The families answer the same question from unrelated geometry — an option button reports the bounds its UI laid
 * its child buttons out at, the other two compute halves from their own size — so the contract is worth checking
 * against all of them at once rather than against whichever one a caller happened to be written for.
 */
@TestApplication
internal class SplitButtonZonesContractTest {

  @ParameterizedTest
  @EnumSource(SplitButtonFamily::class)
  fun `zones and half components honor the contract`(family: SplitButtonFamily): Unit = runBlocking(Dispatchers.EDT) {
    withSplitButtonUiClasses {
      val component = family.create()
      val initialActionZone = assertContract(family, component)

      component.size = Dimension(component.width + 40, component.height)
      component.doLayout()
      val resizedActionZone = assertContract(family, component)

      assertThat(resizedActionZone.width)
        .describedAs("$family must recompute its action zone from the current size, not report a stale one")
        .isGreaterThan(initialActionZone.width)
    }
  }

  /** @return the action zone, the one half every family has. */
  private fun assertContract(family: SplitButtonFamily, component: Component): Rectangle {
    val bounds = Rectangle(component.size)
    val action = assertHalf(family, component, bounds, SplitButtonHalf.ACTION)!!
    val expand = assertHalf(family, component, bounds, SplitButtonHalf.EXPAND)
                 ?: return action

    // The contract promises that the centre of a half lies in that half and in no other. It promises neither
    // disjoint halves (the Windows 10 option button overlaps them) nor halves that tile the component (a toolbar
    // split button keeps its separator out of both), so neither is asserted here.
    assertThat(action.contains(centreOf(action)))
      .describedAs("$family action centre must lie in the action zone")
      .isTrue()
    assertThat(expand.contains(centreOf(action)))
      .describedAs("$family action centre $action must not lie in the expand zone $expand")
      .isFalse()
    assertThat(expand.contains(centreOf(expand)))
      .describedAs("$family expand centre must lie in the expand zone")
      .isTrue()
    assertThat(action.contains(centreOf(expand)))
      .describedAs("$family expand centre $expand must not lie in the action zone $action")
      .isFalse()
    return action
  }

  private fun assertHalf(
    family: SplitButtonFamily,
    component: Component,
    bounds: Rectangle,
    half: SplitButtonHalf,
  ): Rectangle? {
    val zones = component as SplitButtonZones
    val zone = zones.splitButtonZone(half)
    val halfComponent = zones.splitButtonHalfComponent(half)

    if (half == SplitButtonHalf.EXPAND && !family.hasExpandHalf) {
      assertThat(zone).describedAs("$family has no expand half, so it must report no zone").isNull()
      assertThat(halfComponent).describedAs("$family has no expand half, so it must report no component").isNull()
      return null
    }

    assertThat(zone).describedAs("$family must report its $half zone").isNotNull()
    assertThat(zone!!.isEmpty).describedAs("$family $half zone must not be empty: $zone").isFalse()
    assertThat(bounds.contains(zone)).describedAs("$family $half zone $zone must lie inside $bounds").isTrue()

    assertThat(halfComponent).describedAs("$family must report the component owning its $half listeners").isNotNull()
    if (halfComponent !== component) {
      assertThat(SwingUtilities.isDescendingFrom(halfComponent, component as Container))
        .describedAs("$family $half half component must be the button itself or one of its descendants")
        .isTrue()
      assertThat(halfComponent!!.bounds)
        .describedAs("$family $half half component must be laid out at its zone")
        .isEqualTo(zone)
    }
    return zone
  }

  internal enum class SplitButtonFamily(val hasExpandHalf: Boolean) {
    OPTION_BUTTON_BASIC(hasExpandHalf = true) {
      override fun create(): Component = createOptionButton(BasicOptionButtonUI(), withOptions = true)
    },
    OPTION_BUTTON_DARCULA(hasExpandHalf = true) {
      override fun create(): Component = createOptionButton(DarculaOptionButtonUI(), withOptions = true)
    },
    OPTION_BUTTON_OVERLAPPING_HALVES(hasExpandHalf = true) {
      override fun create(): Component = createOptionButton(OverlappingHalvesOptionButtonUI(), withOptions = true)
    },
    SIMPLE_OPTION_BUTTON(hasExpandHalf = false) {
      override fun create(): Component = createOptionButton(BasicOptionButtonUI(), withOptions = false)
    },
    TOOLBAR_SPLIT_BUTTON(hasExpandHalf = true) {
      override fun create(): Component = createToolbarSplitButton()
    },
    ACTION_SYSTEM_SPLIT_BUTTON(hasExpandHalf = true) {
      override fun create(): Component = createActionSystemSplitButton()
    };

    abstract fun create(): Component
  }
}

/**
 * The Windows 10 option button layout, whose halves overlap around their boundary.
 *
 * It is the reason [SplitButtonZones] promises the centre of a half instead of disjoint halves, so the contract
 * test needs it — and `WinIntelliJOptionButtonUI`, which ships it, lives in a LaF plugin that platform-tests does
 * not depend on.
 */
private class OverlappingHalvesOptionButtonUI : BasicOptionButtonUI() {
  override fun createLayoutManager(): LayoutManager = object : OptionButtonLayout() {
    override fun layoutContainer(parent: Container) = layOutOverlappingHalves()
  }

  /** The halves are protected members of this UI, so they are read here rather than from the layout manager. */
  private fun layOutOverlappingHalves() {
    val arrowWidth = if (arrowButton.isVisible) arrowButton.preferredSize.width else 0
    val overlap = if (arrowButton.isVisible) JBUI.scale(2) else 0
    val mainButtonWidth = optionButton.width - arrowWidth

    mainButton.bounds = Rectangle(overlap, 0, mainButtonWidth, optionButton.height)
    arrowButton.bounds = Rectangle(mainButtonWidth - overlap, 0, arrowWidth, optionButton.height)
  }
}

private fun createOptionButton(ui: OptionButtonUI, withOptions: Boolean): JBOptionButton {
  val mainAction = object : AbstractAction("New Session", AllIcons.General.Add) {
    override fun actionPerformed(e: ActionEvent?) = Unit
  }
  return JBOptionButton(mainAction, null).apply {
    setUI(ui)
    if (withOptions) {
      setOptions(listOf(DumbAwareAction.create("Profile") {}))
    }
    size = preferredSize
    doLayout()
  }
}

private fun createToolbarSplitButton(): ToolbarSplitButton {
  return ToolbarSplitButton(DefaultToolbarSplitButtonModel()).apply {
    text = "New Session"
    leftIcons = listOf(AllIcons.General.Add)
    font = Font(Font.DIALOG, Font.PLAIN, 12)
    size = Dimension(160, 30)
    doLayout()
  }
}

private fun createActionSystemSplitButton(): Component {
  val profile = DumbAwareAction.create("Profile") {}.apply { templatePresentation.icon = AllIcons.General.Add }
  val action = SplitButtonAction(DefaultActionGroup(profile))
  val presentation = action.templatePresentation.clone().apply { icon = AllIcons.General.Add }
  return action.createCustomComponent(presentation, "SplitButtonZonesContractTest").apply {
    size = preferredSize
    doLayout()
  }
}

private val SPLIT_BUTTON_UI_CLASSES = mapOf(
  "OptionButtonUI" to BasicOptionButtonUI::class.java.name,
  "ToolbarSplitButtonUI" to ToolbarSplitButtonUI::class.java.name,
)

/**
 * Installs the UI classes the fixtures need, because a bare test application has no LaF that registers them.
 *
 * A toolbar split button has no public way to be handed a UI, so registration is the only route; an option button
 * is handed one directly and this keeps its construction from looking one up and failing.
 */
private fun <T> withSplitButtonUiClasses(block: () -> T): T {
  val defaults = UIManager.getDefaults()
  val previous = SPLIT_BUTTON_UI_CLASSES.keys.associateWith { uiClassId -> defaults[uiClassId] }
  SPLIT_BUTTON_UI_CLASSES.forEach { (uiClassId, uiClassName) -> defaults[uiClassId] = uiClassName }
  try {
    return block()
  }
  finally {
    for ((uiClassId, uiClass) in previous) {
      if (uiClass == null) defaults.remove(uiClassId) else defaults[uiClassId] = uiClass
    }
  }
}

private fun centreOf(zone: Rectangle): Point = Point(zone.x + zone.width / 2, zone.y + zone.height / 2)
