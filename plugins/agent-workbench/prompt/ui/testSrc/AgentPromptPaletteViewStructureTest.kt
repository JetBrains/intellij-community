// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.Advertiser
import com.intellij.util.ui.JBUI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.abs

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPaletteViewStructureTest {
  @Test
  fun promptAreaIsRenderedExactlyOnceAndSharedAcrossTabs() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )

      val rootPanel = view.rootPanel
      val promptAreasBeforeSwitch = collectComponentsOfType(rootPanel, EditorTextField::class.java)
      assertThat(promptAreasBeforeSwitch).containsExactly(promptArea)

      view.tabbedPane.selectedIndex = 1
      view.existingTaskScrollPane.isVisible = true
      layoutPopupRoot(rootPanel)

      val promptAreasAfterSwitch = collectComponentsOfType(rootPanel, EditorTextField::class.java)
      assertThat(promptAreasAfterSwitch).containsExactly(promptArea)
    }
  }

  @Test
  fun promptAreaInstanceRemainsStableWhenTargetModeChanges() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )

      val rootPanel = view.rootPanel
      layoutPopupRoot(rootPanel)
      val initialPromptArea = checkNotNull(findPromptArea(rootPanel, promptArea))

      view.tabbedPane.selectedIndex = 0
      view.existingTaskScrollPane.isVisible = false
      layoutPopupRoot(rootPanel)
      assertThat(findPromptArea(rootPanel, promptArea)).isSameAs(initialPromptArea)

      view.tabbedPane.selectedIndex = 1
      view.existingTaskScrollPane.isVisible = true
      layoutPopupRoot(rootPanel)
      assertThat(findPromptArea(rootPanel, promptArea)).isSameAs(initialPromptArea)
    }
  }

  @Test
  fun providerOptionActionsAreRenderedOnceInsideHeader() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val planModeAction = AgentPromptHeaderCheckBoxAction("&Plan mode")
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      layoutPopupRoot(view.rootPanel)

      val planModeCheckBoxes = collectComponentsOfType(view.rootPanel, JBCheckBox::class.java).filter { it.text == "Plan mode" }
      val planModeCheckBox = planModeCheckBoxes.single()
      assertThat(view.headerControls.providerOptionActions).containsExactly(planModeAction)
      assertThat(SwingUtilities.isDescendingFrom(planModeCheckBox, view.headerControls.toolbarComponent)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(planModeCheckBox, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(planModeCheckBox, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(planModeCheckBox, view.promptEditorPanel)).isFalse()
      assertThat(planModeCheckBox.isFocusable).isFalse()
    }
  }

  @Test
  fun rightHeaderPanelIsRightAlignedAfterTargetTabs() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val planModeAction = AgentPromptHeaderCheckBoxAction("&Plan mode")
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      view.headerControls.setContainerModeVisible(true)

      layoutPopupRoot(view.rootPanel)

      val tabbedPaneX = xInRoot(view.tabbedPane, view.rootPanel)
      val rightHeaderX = xInRoot(view.rightHeaderPanel, view.rootPanel)
      assertThat(view.headerToolbar.layoutStrategy).isSameAs(ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY)
      assertThat(SwingUtilities.isDescendingFrom(view.profileAction.customComponent, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.promptLibraryIconLabel, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.headerControls.toolbarComponent, view.rightHeaderPanel)).isTrue()
      assertThat(rightHeaderX).isGreaterThan(tabbedPaneX)
      assertThat(rightHeaderX + view.rightHeaderPanel.width).isLessThanOrEqualTo(view.rootPanel.width)
    }
  }

  @Test
  fun popupDefaultWidthStaysStableWhenHeaderOptionsAreVisible() {
    runInEdtAndWait {
      val planModeAction = AgentPromptHeaderCheckBoxAction("&Plan mode")
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      view.headerControls.setContainerModeVisible(true)

      layoutPopupRoot(view.rootPanel)

      assertThat(view.rootPanel.preferredSize.width).isEqualTo(680)
      assertThat(view.rootPanel.minimumSize.width).isEqualTo(520)
      assertThat(view.rootPanel.minimumSize.width).isLessThan(view.rootPanel.preferredSize.width)
      assertThat(view.headerControls.toolbarComponent.minimumSize.width)
        .isLessThan(view.headerControls.toolbarComponent.preferredSize.width)
      assertThat(view.rootPanel.width).isEqualTo(view.rootPanel.preferredSize.width)
    }
  }

  @Test
  fun headerToolbarDoesNotForcePopupWiderThanMinimumWidth() {
    runInEdtAndWait {
      val planModeAction = AgentPromptHeaderCheckBoxAction("&Plan mode")
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      view.headerControls.setContainerModeVisible(true)
      val narrowSize = Dimension(view.rootPanel.minimumSize.width, view.rootPanel.preferredSize.height)

      layoutPopupRoot(view.rootPanel, narrowSize)

      val toolbarX = xInRoot(view.headerControls.toolbarComponent, view.rootPanel)
      assertThat(view.rootPanel.width).isEqualTo(narrowSize.width)
      assertThat(view.headerControls.toolbarComponent.width).isLessThanOrEqualTo(view.rightHeaderPanel.width)
      assertThat(toolbarX + view.headerControls.toolbarComponent.width).isLessThanOrEqualTo(view.rootPanel.width)
    }
  }

  @Test
  fun containerModeControlIsAHeaderLaunchOption() {
    runInEdtAndWait {
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setContainerModeVisible(true)
      layoutPopupRoot(view.rootPanel)
      val containerModeCheckBox = findHeaderCheckBox(view, "Run in container")

      assertThat(SwingUtilities.isDescendingFrom(containerModeCheckBox, view.headerControls.toolbarComponent)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(containerModeCheckBox, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(containerModeCheckBox, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(containerModeCheckBox, view.promptEditorPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(containerModeCheckBox, view.bottomPanel)).isFalse()
      assertThat(containerModeCheckBox.text).isEqualTo("Run in container")
      assertThat(containerModeCheckBox.mnemonic).isEqualTo(KeyEvent.VK_R)
      assertThat(containerModeCheckBox.displayedMnemonicIndex).isEqualTo(0)
      assertThat(containerModeCheckBox.isFocusable).isFalse()
      assertThat(containerModeCheckBox.isOpaque).isFalse()
      assertThat(containerModeCheckBox.font).isEqualTo(JBCheckBox().font)
    }
  }

  @Test
  fun headerOptionCheckBoxesUseSearchEverywhereCheckboxStyle() {
    runInEdtAndWait {
      val planModeAction = AgentPromptHeaderCheckBoxAction("&Plan mode")
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      view.headerControls.setContainerModeVisible(true)
      layoutPopupRoot(view.rootPanel)
      val planModeCheckBox = findHeaderCheckBox(view, "Plan mode")
      val containerModeCheckBox = findHeaderCheckBox(view, "Run in container")

      val defaultCheckBox = JBCheckBox()
      assertThat(planModeCheckBox.font).isEqualTo(defaultCheckBox.font)
      assertThat(containerModeCheckBox.font).isEqualTo(defaultCheckBox.font)
      assertThat(planModeCheckBox.isOpaque).isEqualTo(containerModeCheckBox.isOpaque)
      assertThat(planModeCheckBox.isFocusable).isEqualTo(containerModeCheckBox.isFocusable)
      assertThat(planModeCheckBox.border.getBorderInsets(planModeCheckBox))
        .isEqualTo(containerModeCheckBox.border.getBorderInsets(containerModeCheckBox))
      assertThat(abs(yCenterInRoot(planModeCheckBox, view.rootPanel) - yCenterInRoot(containerModeCheckBox, view.rootPanel)))
        .isLessThanOrEqualTo(1)
    }
  }

  @Test
  fun statusStripUsesCompactBigPopupAdvertiserChrome() {
    runInEdtAndWait {
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      val referenceAdvertiser = Advertiser().apply {
        setBorder(JBUI.CurrentTheme.BigPopup.advertiserBorder())
        setBackground(JBUI.CurrentTheme.BigPopup.advertiserBackground())
        setForeground(JBUI.CurrentTheme.BigPopup.advertiserForeground())
        addAdvertisement("Enter to send", null)
      }

      layoutPopupRoot(view.rootPanel)

      assertThat(SwingUtilities.isDescendingFrom(view.statusStrip.component, view.bottomPanel)).isTrue()
      assertThat(view.statusStrip.component.preferredSize.height)
        .isEqualTo(referenceAdvertiser.adComponent.preferredSize.height)
      assertThat(view.statusStrip.component.preferredSize.height)
        .isLessThan(view.rootPanel.preferredSize.height / 10)
    }
  }

  @Test
  fun promptLibraryControlIsSingleHeaderEntry() {
    runInEdtAndWait {
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onPromptLibraryClicked = {},
        onExistingTaskSelected = {},
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(SwingUtilities.isDescendingFrom(view.promptLibraryIconLabel, view.rootPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.promptLibraryIconLabel, view.rightHeaderPanel)).isTrue()
      assertThat(view.promptLibraryIconLabel.toolTipText).isEqualTo(AgentPromptBundle.message("popup.prompt.library.tooltip"))
      assertThat(view.promptLibraryIconLabel.preferredSize).isEqualTo(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
      assertThat(view.promptLibraryIconLabel.width).isEqualTo(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width)
      assertThat(view.promptLibraryIconLabel.horizontalAlignment).isEqualTo(SwingConstants.CENTER)
    }
  }

  @Test
  fun taskCostProfileControlIsSingleHeaderEntryAndGenerationControlsAreInsidePromptEditor() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.updateActions()
      layoutPopupRoot(view.rootPanel)
      val promptAreaInRoot = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      val profileActionComponent = view.profileAction.customComponent
      assertThat(profileActionComponent).isSameAs(view.launchProfileLink)
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.rootPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(profileActionComponent, view.rootPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(profileActionComponent, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(profileActionComponent, view.headerControls.toolbarComponent)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.promptEditorPanel)).isFalse()
      assertThat(view.profileAction.templatePresentation.description).contains("Choose a task profile")
      assertThat(SwingUtilities.isDescendingFrom(profileActionComponent, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.modelSelectorLink, view.rootPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.reasoningEffortLink, view.rootPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.planReasoningEffortLink, view.rootPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.modelSelectorLink, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.reasoningEffortLink, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.planReasoningEffortLink, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.defaultProfileActionControl.component, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.defaultProfileActionControl.component, view.rightHeaderPanel)).isFalse()
      assertThat(view.generationSettingsPanel.parent).isSameAs(view.promptEditorPanel)
      assertThat(SwingUtilities.isDescendingFrom(promptAreaInRoot, view.promptEditorPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.promptEditorPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.bottomPanel)).isFalse()
      assertThat(view.promptEditorPanel.border).isNotNull()
      assertThat(view.generationSettingsPanel.isOpaque).isFalse()
      assertThat(yCenterInRoot(view.generationSettingsPanel, view.rootPanel)).isGreaterThan(yCenterInRoot(promptAreaInRoot, view.rootPanel))
      assertThat(yInRoot(view.generationSettingsPanel, view.rootPanel)).isGreaterThanOrEqualTo(bottomInRoot(promptAreaInRoot, view.rootPanel))
      assertThat(bottomInRoot(view.generationSettingsPanel, view.rootPanel)).isLessThanOrEqualTo(bottomInRoot(view.promptEditorPanel, view.rootPanel))
      assertThat(promptAreaInRoot.border.getBorderInsets(promptAreaInRoot).bottom).isZero()
      assertThat(view.generationSettingsPanel.isVisible).isTrue()
      assertThat(view.launchProfileLink.text).isEqualTo("Standard")
      assertThat(view.profileAction.textForTest).isEqualTo("Standard")
      assertThat(view.launchProfileLink.icon).isNotNull()
      assertThat(view.modelSelectorLink.foreground).isEqualTo(view.reasoningEffortLink.foreground)
      assertThat(view.modelSelectorLink.text).isEqualTo("Model Default")
      assertThat(view.reasoningEffortLink.text).isEqualTo("Effort Default")
      assertThat(view.modelSelectorLink.isEnabled).isTrue()
      val modelSelectorCenter = SwingUtilities.convertPoint(
        view.modelSelectorLink,
        view.modelSelectorLink.width / 2,
        view.modelSelectorLink.height / 2,
        view.rootPanel,
      )
      val topComponent = SwingUtilities.getDeepestComponentAt(view.rootPanel, modelSelectorCenter.x, modelSelectorCenter.y)
      assertThat(isDescendantOrSame(topComponent, view.generationSettingsPanel)).isTrue()
    }
  }

  @Test
  fun productionPromptFieldDoesNotReserveOverlaySpaceForGenerationSettingsControls() {
    runInEdtAndWait {
      val disposable = Disposer.newDisposable()
      try {
        val promptArea = AgentPromptTextField(ProjectManager.getInstance().defaultProject).apply {
          setDisposedWith(disposable)
        }
        val view = createAgentPromptPaletteView(
          promptArea = promptArea,
          contextChipsPanel = JPanel(),
          onExistingTaskSelected = {},
        )

        layoutPopupRoot(view.rootPanel)
        val editor = checkNotNull(promptArea.getEditor(true))
        assertThat(editor.scrollPane.verticalScrollBarPolicy).isEqualTo(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)
        assertThat(promptArea.border.getBorderInsets(promptArea).bottom).isZero()
        assertThat(view.generationSettingsPanel.parent).isSameAs(view.promptEditorPanel)
        assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.rootPanel)).isTrue()
        assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.promptEditorPanel)).isTrue()
        assertThat(yInRoot(view.generationSettingsPanel, view.rootPanel)).isGreaterThanOrEqualTo(bottomInRoot(promptArea, view.rootPanel))
        val modelSelectorCenter = SwingUtilities.convertPoint(
          view.modelSelectorLink,
          view.modelSelectorLink.width / 2,
          view.modelSelectorLink.height / 2,
          view.rootPanel,
        )
        val topComponent = SwingUtilities.getDeepestComponentAt(view.rootPanel, modelSelectorCenter.x, modelSelectorCenter.y)
        assertThat(isDescendantOrSame(topComponent, view.generationSettingsPanel)).isTrue()
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
  }

  @Test
  fun addContextControlUsesTextLabelAndInlineMnemonic() {
    runInEdtAndWait {
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )

      assertThat(view.addContextButton.text).isEqualTo("Add Context")
      assertThat(view.addContextButton.mnemonic).isEqualTo(KeyEvent.VK_C)
      assertThat(view.addContextButton.displayedMnemonicIndex).isEqualTo(4)
    }
  }

  @Test
  fun composerContextControlsArePlacedInsidePromptPanel() {
    runInEdtAndWait {
      val contextChipsPanel = JPanel().apply {
        add(JPanel())
      }
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = contextChipsPanel,
        onExistingTaskSelected = {},
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.bottomPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.bottomPanel)).isFalse()
    }
  }

  @Test
  fun suggestionsPanelIsPlacedInsidePromptPanel() {
    runInEdtAndWait {
      val suggestionsPanel = JPanel()
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        suggestionsPanel = suggestionsPanel,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(view.suggestionsPanel).isSameAs(suggestionsPanel)
      assertThat(SwingUtilities.isDescendingFrom(suggestionsPanel, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(suggestionsPanel, view.bottomPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(suggestionsPanel, view.composerContextPanel)).isFalse()
    }
  }

  private fun xInRoot(component: Component, root: JPanel): Int {
    return SwingUtilities.convertPoint(component.parent, component.location, root).x
  }

  private fun yInRoot(component: Component, root: JPanel): Int {
    return SwingUtilities.convertPoint(component.parent, component.location, root).y
  }

  private fun bottomInRoot(component: Component, root: JPanel): Int {
    return yInRoot(component, root) + component.height
  }

  private fun isDescendantOrSame(component: Component?, ancestor: Component): Boolean {
    return component === ancestor || component != null && SwingUtilities.isDescendingFrom(component, ancestor)
  }

  private fun yCenterInRoot(component: Component, root: JPanel): Int {
    val y = SwingUtilities.convertPoint(component.parent, component.location, root).y
    return y + component.height / 2
  }

  private fun findHeaderCheckBox(view: AgentPromptPaletteView, text: String): JBCheckBox {
    view.headerControls.updateActions()
    layoutPopupRoot(view.rootPanel)
    return collectComponentsOfType(view.rootPanel, JBCheckBox::class.java).single { checkBox -> checkBox.text == text }
  }
}
