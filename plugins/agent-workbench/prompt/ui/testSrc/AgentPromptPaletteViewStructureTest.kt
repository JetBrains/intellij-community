// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec plugins/ij-air/spec/actions/global-prompt-composer.spec.md

import com.intellij.agent.workbench.prompt.ui.icons.AgentWorkbenchPromptUIIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.Advertiser
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
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
      val planModeAction = createPlanModeHeaderAction()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      layoutPopupRoot(view.rootPanel)

      val planModeButton = findHeaderActionButton(view, planModeAction)
      assertThat(view.headerControls.providerOptionActions).containsExactly(planModeAction)
      assertThat(planModeAction.templatePresentation.text).isEqualTo("Plan mode")
      assertThat(planModeAction.templatePresentation.icon).isSameAs(AgentWorkbenchPromptUIIcons.PlanMode)
      assertThat(SwingUtilities.isDescendingFrom(planModeButton, view.headerControls.toolbarComponent)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(planModeButton, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(planModeButton, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(planModeButton, view.promptEditorPanel)).isFalse()
      assertThat(collectComponentsOfType(view.rootPanel, JBCheckBox::class.java).map { checkBox -> checkBox.text })
        .doesNotContain("Plan mode")
    }
  }

  @Test
  fun rightHeaderPanelIsRightAlignedAfterTargetTabs() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val planModeAction = createPlanModeHeaderAction()
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
      assertThat(SwingUtilities.isDescendingFrom(view.profileAction.customComponent, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.promptLibraryIconLabel, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.headerControls.toolbarComponent, view.rightHeaderPanel)).isTrue()
      assertThat(rightHeaderX).isGreaterThan(tabbedPaneX)
      assertThat(rightHeaderX + view.rightHeaderPanel.width).isLessThanOrEqualTo(view.rootPanel.width)
    }
  }

  @Test
  fun popupDefaultWidthStaysStableWhenHeaderOptionsAreVisible() {
    runInEdtAndWait {
      val planModeAction = createPlanModeHeaderAction()
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      view.headerControls.setContainerModeVisible(true)

      layoutPopupRoot(view.rootPanel)

      assertThat(view.rootPanel.preferredSize.width).isEqualTo(680)
      assertThat(view.rootPanel.preferredSize.height).isEqualTo(400)
      assertThat(view.rootPanel.minimumSize.width).isEqualTo(520)
      assertThat(view.rootPanel.minimumSize.width).isLessThan(view.rootPanel.preferredSize.width)
      assertThat(view.headerControls.toolbarComponent.minimumSize.width)
        .isLessThan(view.headerControls.toolbarComponent.preferredSize.width)
      assertThat(view.rootPanel.width).isEqualTo(view.rootPanel.preferredSize.width)
    }
  }

  @Test
  fun inlineEmptyStatePresentationKeepsCompactGlobalPromptChrome() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val planModeAction = createPlanModeHeaderAction()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))

      layoutPopupRoot(view.rootPanel)
      val promptAreaInRoot = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      assertThat(view.rootPanel.preferredSize.height).isLessThan(AGENT_PROMPT_PALETTE_PREFERRED_SIZE.height)
      assertThat(view.rootPanel.minimumSize.height).isLessThan(AGENT_PROMPT_PALETTE_MINIMUM_SIZE.height)
      assertThat(view.tabbedPane.isVisible).isFalse()
      assertThat(view.rootPanel.isOpaque).isFalse()
      assertThat(view.headerPanel.isOpaque).isFalse()
      assertThat(totalBorderInsets(view.headerPanel)).isGreaterThan(0)
      assertThat(view.existingTaskScrollPane.isVisible).isFalse()
      assertThat(view.footerPanel.isVisible).isFalse()
      assertThat(view.footerPinToolbar.component.isVisible).isFalse()
      assertThat(view.promptEditorPanel.border).isInstanceOf(JBEmptyBorder::class.java)
      assertThat(totalBorderInsets(view.promptEditorPanel)).isGreaterThan(0)
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.headerControls.toolbarComponent)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.composerContextPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.composerContextPanel, view.promptEditorPanel)).isTrue()
      assertThat(xInRoot(view.addContextButton, view.rootPanel)).isLessThan(xInRoot(view.launchProfileLink, view.rootPanel))
      assertThat(SwingUtilities.isDescendingFrom(view.headerControls.toolbarComponent, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.profileAction.customComponent, view.rightHeaderPanel)).isFalse()
      assertThat(view.launchProfileLink.isFocusable).isFalse()
      assertThat(view.launchTuningSummaryLink.isFocusable).isFalse()
      assertThat(view.addContextButton.isFocusable).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(promptAreaInRoot, view.promptEditorPanel)).isTrue()
      assertThat(promptAreaInRoot.height).isGreaterThan(0)
    }
  }

  @Test
  fun clickingInlinePromptChromeRequestsPromptFocus() {
    runInEdtAndWait {
      val promptArea = FocusTrackingEditorTextField()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )

      layoutPopupRoot(view.rootPanel)
      val topLeftChrome = checkNotNull(SwingUtilities.getDeepestComponentAt(view.rootPanel, 2, 2))

      triggerMousePressed(topLeftChrome)

      assertThat(promptArea.focusRequested).isTrue()
    }
  }

  @Test
  fun clickingInlinePromptControlsDoesNotRequestPromptFocusThroughChromeForwarding() {
    runInEdtAndWait {
      val promptArea = FocusTrackingEditorTextField()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )

      layoutPopupRoot(view.rootPanel)

      triggerMousePressed(view.addContextButton)
      triggerMousePressed(view.launchProfileLink)

      assertThat(promptArea.focusRequested).isFalse()
    }
  }

  @Test
  fun inlineEmptyStateKeepsPromptUsableAtMinimumSize() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val planModeAction = createPlanModeHeaderAction()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      view.headerControls.setContainerModeVisible(true)

      layoutPopupRoot(view.rootPanel, view.rootPanel.minimumSize)
      val promptAreaInRoot = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      assertThat(view.rootPanel.width).isEqualTo(view.rootPanel.minimumSize.width)
      assertThat(promptAreaInRoot.height).isGreaterThan(0)
      assertThat(view.generationSettingsPanel.height).isGreaterThan(0)
      assertThat(view.addContextButton.isVisible).isTrue()
      assertThat(view.footerPanel.isVisible).isFalse()
      val generationSettingsBottom = bottomInRoot(view.generationSettingsPanel, view.rootPanel)
      val promptPanelBottom = bottomInRoot(view.promptPanel, view.rootPanel)
      assertThat(generationSettingsBottom).isLessThanOrEqualTo(promptPanelBottom)
    }
  }

  @Test
  fun headerToolbarDoesNotForcePopupWiderThanMinimumWidth() {
    runInEdtAndWait {
      val planModeAction = createPlanModeHeaderAction()
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
      val containerModeCheckBox = findContainerModeCheckBox(view)

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
  fun planModeControlUsesIconOnlyHeaderButton() {
    runInEdtAndWait {
      val planModeAction = createPlanModeHeaderAction()
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.headerControls.setProviderOptionActions(listOf(planModeAction))
      view.headerControls.setContainerModeVisible(true)
      layoutPopupRoot(view.rootPanel)
      val planModeButton = findHeaderActionButton(view, planModeAction)
      val containerModeCheckBox = findContainerModeCheckBox(view)

      assertThat(planModeAction.templatePresentation.text).isEqualTo("Plan mode")
      assertThat(planModeAction.templatePresentation.description).isEqualTo("Plan mode")
      assertThat(planModeAction.templatePresentation.icon).isSameAs(AgentWorkbenchPromptUIIcons.PlanMode)
      assertThat(collectComponentsOfType(view.rootPanel, JBCheckBox::class.java).map { checkBox -> checkBox.text })
        .doesNotContain("Plan mode")
      assertThat(abs(yCenterInRoot(planModeButton, view.rootPanel) - yCenterInRoot(containerModeCheckBox, view.rootPanel)))
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
      val referenceAdvertiserHeight = referenceAdvertiser.adComponent.preferredSize.height
      val footerPinToolbarSize = view.footerPinToolbar.component.preferredSize
      val footerPinToolbarInsets = view.footerPinToolbar.component.insets

      assertThat(SwingUtilities.isDescendingFrom(view.footerPanel, view.bottomPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.statusStrip.component, view.footerPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.statusStrip.component, view.bottomPanel)).isTrue()
      assertThat(view.footerPanel.background).isEqualTo(referenceAdvertiser.adComponent.background)
      assertThat(view.statusStrip.component.border.getBorderInsets(view.statusStrip.component))
        .isEqualTo(referenceAdvertiser.adComponent.border.getBorderInsets(referenceAdvertiser.adComponent))
      assertThat(footerPinToolbarSize.height).isEqualTo(referenceAdvertiserHeight)
      assertThat(footerPinToolbarSize.width)
        .isLessThanOrEqualTo(referenceAdvertiserHeight + footerPinToolbarInsets.left + footerPinToolbarInsets.right)
      assertThat(view.footerPanel.preferredSize.height)
        .isEqualTo(referenceAdvertiserHeight)
      assertThat(view.footerPanel.preferredSize.height)
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
      assertThat(collectComponentsOfType(view.rootPanel, JBLabel::class.java).map { it.toolTipText })
        .doesNotContain("Toggle Markdown Preview")
      assertThat(view.promptLibraryIconLabel.preferredSize).isEqualTo(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
      assertThat(view.promptLibraryIconLabel.width).isEqualTo(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width)
      assertThat(view.promptLibraryIconLabel.horizontalAlignment).isEqualTo(SwingConstants.CENTER)
    }
  }

  @Test
  fun keepOpenControlIsASecondaryFooterEntry() {
    runInEdtAndWait {
      var pinned = false
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        pinned = { pinned },
        onPinClicked = { pinned = !pinned },
        onExistingTaskSelected = {},
      )
      layoutPopupRoot(view.rootPanel)
      val event = AnActionEvent.createEvent(
        view.footerPinAction,
        DataContext.EMPTY_CONTEXT,
        null,
        "",
        ActionUiKind.TOOLBAR,
        null,
      )

      assertThat(view.footerPinToolbar.component.parent).isSameAs(view.footerPanel)
      assertThat(SwingUtilities.isDescendingFrom(view.footerPinToolbar.component, view.footerPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.footerPinToolbar.component, view.bottomPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.footerPinToolbar.component, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.footerPinToolbar.component, view.headerControls.toolbarComponent)).isFalse()
      assertThat(view.footerPinToolbar.component.isOpaque).isFalse()
      assertThat(view.footerPinAction.templatePresentation.text).isEqualTo("Keep Popup Open")
      assertThat(view.footerPinAction.isSelected(event)).isFalse()

      view.footerPinAction.actionPerformed(event)

      assertThat(pinned).isTrue()
      assertThat(view.footerPinAction.isSelected(event)).isTrue()
    }
  }

  @Test
  fun launchSettingsAndAddContextControlsAreComposerTrayEntries() {
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
      assertThat(profileActionComponent).isNotSameAs(view.launchProfileLink)
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.rootPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, profileActionComponent)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.profileAction.iconLabel, profileActionComponent)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(profileActionComponent, view.rootPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(profileActionComponent, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(profileActionComponent, view.headerControls.toolbarComponent)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.promptEditorPanel)).isTrue()
      assertThat(view.profileAction.templatePresentation.description).contains("Change launch profile, model, and reasoning")
      assertThat(SwingUtilities.isDescendingFrom(profileActionComponent, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.rootPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.headerControls.toolbarComponent)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.modelSelectorLink, view.rootPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.reasoningEffortLink, view.rootPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.modelSelectorLink, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.reasoningEffortLink, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.defaultProfileActionControl.component, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.defaultProfileActionControl.component, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.generationSettingsPanel)).isTrue()
      assertThat(xInRoot(view.addContextButton, view.rootPanel)).isLessThan(xInRoot(view.launchProfileLink, view.rootPanel))
      assertThat(view.generationSettingsPanel.parent).isNotNull()
      assertThat(SwingUtilities.isDescendingFrom(promptAreaInRoot, view.promptEditorPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.promptEditorPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.rightHeaderPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.bottomPanel)).isFalse()
      assertThat(view.promptEditorPanel.border).isNotNull()
      assertThat(view.generationSettingsPanel.isOpaque).isFalse()
      assertThat(yCenterInRoot(view.generationSettingsPanel, view.rootPanel)).isGreaterThan(yCenterInRoot(promptAreaInRoot, view.rootPanel))
      assertThat(yInRoot(view.generationSettingsPanel, view.rootPanel)).isGreaterThanOrEqualTo(bottomInRoot(promptAreaInRoot,
                                                                                                            view.rootPanel))
      assertThat(bottomInRoot(view.generationSettingsPanel, view.rootPanel)).isLessThanOrEqualTo(bottomInRoot(view.promptEditorPanel,
                                                                                                              view.rootPanel))
      assertThat(promptAreaInRoot.border.getBorderInsets(promptAreaInRoot).bottom).isZero()
      assertThat(view.generationSettingsPanel.isVisible).isTrue()
      assertThat(view.launchProfileLink.text).isEqualTo("Default")
      assertThat(view.profileAction.textForTest).isEqualTo("Default")
      assertThat(view.profileAction.iconLabel.icon).isNotNull()
      assertThat(view.launchProfileLink.icon).isSameAs(view.addContextButton.icon)
      assertThat(view.launchProfileLink.font.isBold).isFalse()
      assertThat(view.launchTuningSummaryLink.isVisible).isFalse()
      val launchTuningSummaryCenter = SwingUtilities.convertPoint(
        profileActionComponent,
        profileActionComponent.width / 2,
        profileActionComponent.height / 2,
        view.rootPanel,
      )
      val topComponent = SwingUtilities.getDeepestComponentAt(view.rootPanel, launchTuningSummaryCenter.x, launchTuningSummaryCenter.y)
      assertThat(isDescendantOrSame(topComponent, view.generationSettingsPanel)).isTrue()
    }
  }

  @Test
  fun launchSettingsProviderIconOpensSameControlAsDropdownLink() {
    runInEdtAndWait {
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      var popupAnchor: JComponent? = null
      view.profileAction.setPopupHandler { _, anchor -> popupAnchor = anchor }

      triggerMousePressed(view.profileAction.iconLabel)

      assertThat(popupAnchor).isSameAs(view.launchProfileLink)

      popupAnchor = null
      view.launchProfileLink.doClick()

      assertThat(popupAnchor).isSameAs(view.launchProfileLink)
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
        val editorInsets = editor.scrollPane.border.getBorderInsets(editor.scrollPane)
        assertThat(editor.scrollPane.verticalScrollBarPolicy).isEqualTo(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)
        assertThat(editorInsets.left).isZero()
        assertThat(editorInsets.right).isZero()
        assertThat(promptArea.border.getBorderInsets(promptArea).bottom).isZero()
        assertThat(view.generationSettingsPanel.parent).isNotNull()
        assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.rootPanel)).isTrue()
        assertThat(SwingUtilities.isDescendingFrom(view.generationSettingsPanel, view.promptEditorPanel)).isTrue()
        assertThat(xInRoot(view.addContextButton, view.rootPanel)).isEqualTo(xInRoot(promptArea, view.rootPanel))
        assertThat(rightInRoot(view.launchProfileLink, view.rootPanel)).isEqualTo(rightInRoot(promptArea, view.rootPanel))
        assertThat(yInRoot(view.generationSettingsPanel, view.rootPanel)).isGreaterThanOrEqualTo(bottomInRoot(promptArea, view.rootPanel))
        val launchTuningSummaryCenter = SwingUtilities.convertPoint(
          view.launchProfileLink,
          view.launchProfileLink.width / 2,
          view.launchProfileLink.height / 2,
          view.rootPanel,
        )
        val topComponent = SwingUtilities.getDeepestComponentAt(view.rootPanel, launchTuningSummaryCenter.x, launchTuningSummaryCenter.y)
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
      var clicked = false
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onExistingTaskSelected = {},
      )
      view.addContextButton.addActionListener { clicked = true }

      assertThat(view.addContextButton.text).isEqualTo("Add Context")
      assertThat(view.addContextButton.mnemonic).isEqualTo(KeyEvent.VK_C)
      assertThat(view.addContextButton.displayedMnemonicIndex).isEqualTo(4)
      assertThat(view.addContextButton.isFocusable).isTrue()

      view.addContextButton.doClick()

      assertThat(clicked).isTrue()
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
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.composerContextPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.promptEditorPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.generationSettingsPanel)).isFalse()
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

  private fun rightInRoot(component: Component, root: JPanel): Int {
    return xInRoot(component, root) + component.width
  }

  private fun totalBorderInsets(component: JComponent): Int {
    val insets = component.border.getBorderInsets(component)
    return insets.top + insets.left + insets.bottom + insets.right
  }

  private fun isDescendantOrSame(component: Component?, ancestor: Component): Boolean {
    return component === ancestor || component != null && SwingUtilities.isDescendingFrom(component, ancestor)
  }

  private fun yCenterInRoot(component: Component, root: JPanel): Int {
    val y = SwingUtilities.convertPoint(component.parent, component.location, root).y
    return y + component.height / 2
  }

  private fun findContainerModeCheckBox(view: AgentPromptPaletteView): JBCheckBox {
    view.headerControls.updateActions()
    layoutPopupRoot(view.rootPanel)
    return collectComponentsOfType(view.rootPanel, JBCheckBox::class.java).single { checkBox -> checkBox.text == "Run in container" }
  }

  private fun findHeaderActionButton(view: AgentPromptPaletteView, action: AgentPromptHeaderIconToggleAction): ActionButton {
    view.headerControls.updateActions()
    layoutPopupRoot(view.rootPanel)
    return collectComponentsOfType(view.rootPanel, ActionButton::class.java).single { button -> button.action === action }
  }

  private fun createPlanModeHeaderAction(): AgentPromptHeaderIconToggleAction {
    return AgentPromptHeaderIconToggleAction("Plan mode", AgentWorkbenchPromptUIIcons.PlanMode)
  }

  private fun triggerMousePressed(component: Component) {
    val event = MouseEvent(component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 1, 1, 1, false, MouseEvent.BUTTON1)
    component.mouseListeners.forEach { listener -> listener.mousePressed(event) }
  }

  private class FocusTrackingEditorTextField : EditorTextField() {
    var focusRequested: Boolean = false

    override fun requestFocusInWindow(): Boolean {
      focusRequested = true
      return true
    }
  }
}
