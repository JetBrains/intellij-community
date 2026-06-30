// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec plugins/ij-air/spec/actions/global-prompt-composer.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItemIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptScreenshotContextItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.abs

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPaletteViewLayoutTest {
  @Test
  fun composerContextClusterIsHiddenWhenNoContextChipsArePresentAndAddContextControlIsUnavailable() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChipsPanel = JPanel()
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChipsPanel,
        addContextVisible = false,
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(view.composerContextPanel.isVisible).isFalse()
    }
  }

  @Test
  fun popupComposerContextClusterIsHiddenWhenAddContextControlIsInBottomTray() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea = promptArea)

      layoutPopupRoot(view.rootPanel)
      val promptAreaInRoot = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      assertThat(view.composerContextPanel.isVisible).isFalse()
      assertThat(view.addContextButton.isVisible).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.generationSettingsPanel)).isTrue()
      assertThat(locationInRoot(view.addContextButton, view.rootPanel).x)
        .isEqualTo(locationInRoot(promptAreaInRoot, view.rootPanel).x)
      assertThat(rightInRoot(view.launchProfileLink, view.rootPanel))
        .isEqualTo(rightInRoot(promptAreaInRoot, view.rootPanel))
      assertThat(locationInRoot(view.addContextButton, view.rootPanel).x)
        .isLessThan(locationInRoot(view.launchProfileLink, view.rootPanel).x)
      assertThat(abs(yCenterInRoot(view.addContextButton, view.rootPanel) - yCenterInRoot(view.launchProfileLink, view.rootPanel)))
        .isLessThanOrEqualTo(1)
      assertThat(view.addContextButton.font.size).isEqualTo(JBFont.label().size)
      assertThat(view.launchProfileLink.font.size).isEqualTo(JBFont.label().size)
      assertThat(view.addContextButton.foreground).isEqualTo(UIUtil.getLabelForeground())
      assertThat(view.launchProfileLink.foreground).isEqualTo(UIUtil.getLabelForeground())

      view.defaultProfileActionControl.setState(AgentPromptDefaultProfileActionState.MAKE_DEFAULT)
      layoutPopupRoot(view.rootPanel)

      assertThat(view.defaultProfileActionControl.component.border.getBorderInsets(view.defaultProfileActionControl.component).left).isEqualTo(8)
      assertThat(locationInRoot(view.defaultProfileActionControl.component, view.rootPanel).x)
        .isEqualTo(rightInRoot(view.launchProfileLink, view.rootPanel))
      assertThat(rightInRoot(view.defaultProfileActionControl.component, view.rootPanel))
        .isEqualTo(rightInRoot(promptAreaInRoot, view.rootPanel))
    }
  }

  @Test
  fun inlineComposerContextClusterIsHiddenWhenAddContextControlIsInBottomTray() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(
        promptArea = promptArea,
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )

      layoutPopupRoot(view.rootPanel)
      val promptAreaInRoot = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      assertThat(view.addContextButton.isVisible).isTrue()
      assertThat(view.composerContextPanel.isVisible).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchProfileLink, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.generationSettingsPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.generationSettingsPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(view.launchTuningSummaryLink, view.rightHeaderPanel)).isFalse()
      assertThat(locationInRoot(view.addContextButton, view.rootPanel).x)
        .isEqualTo(locationInRoot(promptAreaInRoot, view.rootPanel).x)
      assertThat(rightInRoot(view.launchProfileLink, view.rootPanel))
        .isEqualTo(rightInRoot(promptAreaInRoot, view.rootPanel))
      assertThat(locationInRoot(view.addContextButton, view.rootPanel).x)
        .isLessThan(locationInRoot(view.launchProfileLink, view.rootPanel).x)
      assertThat(view.addContextButton.font.size).isEqualTo(JBUI.Fonts.smallFont().size)
      assertThat(view.launchProfileLink.font.size).isEqualTo(JBUI.Fonts.smallFont().size)
      assertThat(view.addContextButton.foreground).isEqualTo(UIUtil.getContextHelpForeground())
      assertThat(view.launchProfileLink.foreground).isEqualTo(UIUtil.getContextHelpForeground())

      view.defaultProfileActionControl.setState(AgentPromptDefaultProfileActionState.MAKE_DEFAULT)
      layoutPopupRoot(view.rootPanel)

      assertThat(view.defaultProfileActionControl.component.border.getBorderInsets(view.defaultProfileActionControl.component).left).isEqualTo(6)
      assertThat(rightInRoot(view.defaultProfileActionControl.component, view.rootPanel))
        .isEqualTo(rightInRoot(promptAreaInRoot, view.rootPanel))
    }
  }

  @Test
  fun composerContextClusterIsShownWhenContextChipsExist() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChipsPanel = JPanel().apply {
        add(JPanel())
      }
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChipsPanel,
        addContextVisible = false,
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(view.composerContextPanel.isVisible).isTrue()
      assertThat(contextChipsPanel.isVisible).isTrue()
      assertThat(contextChipsPanel.height).isGreaterThan(0)
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.promptEditorPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.generationSettingsPanel)).isFalse()
    }
  }

  @Test
  fun firstSwitchToExistingKeepsPromptAreaVisible() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea)
      populateExistingTasks(view = view, count = 200)

      view.tabbedPane.selectedIndex = 0
      view.existingTaskScrollPane.isVisible = false
      layoutPopupRoot(view.rootPanel)

      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))
      assertThat(foundPromptArea.height).isGreaterThan(0)

      view.tabbedPane.selectedIndex = 1
      view.existingTaskScrollPane.isVisible = true
      layoutPopupRoot(view.rootPanel)

      assertThat(foundPromptArea.height).isGreaterThan(0)
    }
  }

  @Test
  fun repeatedModeSwitchesKeepPromptVisible() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea)
      populateExistingTasks(view = view, count = 150)
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      repeat(8) { iteration ->
        val existingMode = iteration % 2 == 1
        view.tabbedPane.selectedIndex = if (existingMode) 1 else 0
        view.existingTaskScrollPane.isVisible = existingMode
        layoutPopupRoot(view.rootPanel)

        assertThat(foundPromptArea.height).isGreaterThan(0)
      }
    }
  }

  @Test
  fun existingTaskPaneIsBoundedToPreventPromptStarvation() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea)

      assertThat(view.existingTaskList.visibleRowCount).isEqualTo(3)
      assertThat(view.existingTaskScrollPane.preferredSize.height).isGreaterThan(0)
      assertThat(view.existingTaskScrollPane.minimumSize.height).isGreaterThan(0)
      assertThat(view.existingTaskScrollPane.preferredSize.height)
        .isLessThan(view.rootPanel.preferredSize.height)
      assertThat(view.existingTaskScrollPane.minimumSize.height)
        .isLessThan(view.rootPanel.minimumSize.height)
    }
  }

  @Test
  fun removingLastContextChipCollapsesContextRowWithoutHidingPrompt() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChipsPanel = JPanel().apply {
        add(JPanel())
      }
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChipsPanel,
        addContextVisible = false,
      )
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      layoutPopupRoot(view.rootPanel)
      assertThat(view.composerContextPanel.isVisible).isTrue()
      val promptHeightWithContext = foundPromptArea.height
      assertThat(promptHeightWithContext).isGreaterThan(0)

      contextChipsPanel.removeAll()
      layoutPopupRoot(view.rootPanel)

      assertThat(view.composerContextPanel.isVisible).isFalse()
      assertThat(foundPromptArea.height).isGreaterThan(promptHeightWithContext)
    }
  }

  @Test
  fun addContextControlKeepsStablePositionWhenContextChipsAreAdded() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChips = AgentPromptContextChipsComponent {}
      contextChips.render(listOf(createContextEntry(title = "File", body = "src/Main.java")))
      val view = createPaletteView(promptArea = promptArea, contextChipsPanel = contextChips.component)

      layoutPopupRoot(view.rootPanel)
      val initialLocation = locationInRoot(view.addContextButton, view.rootPanel)
      val firstChipLocation = locationInRoot(contextChips.component.components.first(), view.rootPanel)
      val promptEditorLocation = locationInRoot(view.promptEditorPanel, view.rootPanel)
      val promptAreaInRoot = checkNotNull(findPromptArea(view.rootPanel, promptArea))
      val promptAreaLocation = locationInRoot(promptAreaInRoot, view.rootPanel)

      assertThat(initialLocation.x).isEqualTo(promptAreaLocation.x)
      assertThat(initialLocation.x).isLessThan(locationInRoot(view.launchProfileLink, view.rootPanel).x)
      assertThat(firstChipLocation.x).isEqualTo(promptAreaLocation.x)
      assertThat(firstChipLocation.x).isGreaterThanOrEqualTo(promptEditorLocation.x)
      assertThat(firstChipLocation.y).isLessThan(promptAreaLocation.y)
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.generationSettingsPanel)).isTrue()
      val firstAttachmentCard = contextAttachmentCards(contextChips.component).single()
      assertThat(locationInRoot(firstAttachmentCard, view.rootPanel).x).isEqualTo(promptAreaLocation.x)
      assertThat(firstAttachmentCard.isOpaque).isFalse()
      assertThat(firstAttachmentCard.accessibleContext.accessibleName).isEqualTo("File: src/Main.java")
      val removeButton = contextRemoveButtons(firstAttachmentCard).single()
      assertThat(removeButton.isFocusable).isTrue()
      assertThat(removeButton.accessibleContext.accessibleName).isEqualTo("Remove context: File: src/Main.java")

      contextChips.render(
        listOf(
          createContextEntry(title = "File", body = "src/Main.java"),
          createContextEntry(title = "Symbol", body = "main"),
          createContextEntry(title = "Caret Context", body = "6-12"),
        )
      )
      layoutPopupRoot(view.rootPanel)

      assertThat(locationInRoot(view.addContextButton, view.rootPanel)).isEqualTo(initialLocation)
      assertThat(view.composerContextPanel.isVisible).isTrue()
      assertThat(contextChips.component.isVisible).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChips.component, view.composerContextPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChips.component, view.promptEditorPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChips.component, view.generationSettingsPanel)).isFalse()
    }
  }

  @Test
  fun inlineContextChipsAreCappedAtTwoRowsWithOverflowChip() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChips = AgentPromptContextChipsComponent(maxVisibleRows = 2) {}
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChips.component,
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )
      val entries = createManyContextEntries()

      layoutPopupRoot(view.rootPanel)
      contextChips.render(entries)
      layoutPopupRoot(view.rootPanel)
      contextChips.render(entries)
      layoutPopupRoot(view.rootPanel)

      val cards = contextAttachmentCards(contextChips.component)
      val overflowCard = contextOverflowCards(contextChips.component).single()
      val overflowText = overflowCard.accessibleContext.accessibleName
      assertThat(componentRowCount(cards, view.rootPanel)).isLessThanOrEqualTo(2)
      assertThat(overflowText).matches("\\+\\d+")
      assertThat(overflowText.removePrefix("+").toInt()).isEqualTo(entries.size - cards.size + 1)
      assertThat(cards.size).isLessThan(entries.size)
    }
  }

  @Test
  fun inlineContextChipsExpandComposerWithoutHidingPromptInput() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChips = AgentPromptContextChipsComponent(maxVisibleRows = 2) {}
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChips.component,
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )
      val entries = createManyContextEntries()

      layoutPopupRoot(view.rootPanel)
      val promptAreaInRoot = checkNotNull(findPromptArea(view.rootPanel, promptArea))
      val compactPreferredHeight = view.rootPanel.preferredSize.height
      val compactPromptHeight = promptAreaInRoot.height
      assertThat(view.composerContextPanel.isVisible).isFalse()
      assertThat(compactPromptHeight).isGreaterThan(0)

      contextChips.render(entries)
      layoutPopupRoot(view.rootPanel)
      contextChips.render(entries)
      layoutPopupRoot(view.rootPanel)

      val cards = contextAttachmentCards(contextChips.component)
      assertThat(view.composerContextPanel.isVisible).isTrue()
      assertThat(view.rootPanel.preferredSize.height).isGreaterThan(compactPreferredHeight)
      assertThat(componentRowCount(cards, view.rootPanel)).isLessThanOrEqualTo(2)
      assertThat(promptAreaInRoot.height).isGreaterThanOrEqualTo(compactPromptHeight)
      assertThat(bottomInRoot(view.composerContextPanel, view.rootPanel))
        .isLessThanOrEqualTo(yInRoot(promptAreaInRoot, view.rootPanel))
      assertThat(bottomInRoot(promptAreaInRoot, view.rootPanel))
        .isLessThanOrEqualTo(yInRoot(view.generationSettingsPanel, view.rootPanel))
      assertThat(bottomInRoot(view.composerContextPanel, view.rootPanel)).isLessThanOrEqualTo(view.rootPanel.height)

      contextChips.render(emptyList())
      layoutPopupRoot(view.rootPanel)

      assertThat(view.composerContextPanel.isVisible).isFalse()
      assertThat(view.rootPanel.preferredSize.height).isEqualTo(compactPreferredHeight)
      assertThat(promptAreaInRoot.height).isGreaterThanOrEqualTo(compactPromptHeight)
    }
  }

  @Test
  fun inlinePromptTextGrowthExpandsComposerWithoutHidingControls() {
    runInEdtAndWait {
      val promptArea = AgentPromptTextField(ProjectManager.getInstance().defaultProject)
      val view = createPaletteView(
        promptArea = promptArea,
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )

      layoutPopupRoot(view.rootPanel)
      val compactPreferredHeight = view.rootPanel.preferredSize.height
      val compactEditorPreferredHeight = view.promptEditorPanel.preferredSize.height

      promptArea.text = promptText(lineCount = 6)
      view.syncInlineSize()
      layoutPopupRoot(view.rootPanel)

      val promptAreaInRoot = checkNotNull(findPromptArea(view.rootPanel, promptArea))
      assertThat(view.rootPanel.preferredSize.height).isGreaterThan(compactPreferredHeight)
      assertThat(view.promptEditorPanel.preferredSize.height).isGreaterThan(compactEditorPreferredHeight)
      assertThat(promptAreaInRoot.height).isGreaterThan(0)
      assertThat(view.generationSettingsPanel.isVisible).isTrue()
      assertThat(view.generationSettingsPanel.height).isGreaterThan(0)
      assertThat(bottomInRoot(promptAreaInRoot, view.rootPanel))
        .isLessThanOrEqualTo(yInRoot(view.generationSettingsPanel, view.rootPanel))
    }
  }

  @Test
  fun inlinePromptTextGrowthIsCappedAndUsesEditorScrollingAfterLimit() {
    runInEdtAndWait {
      val promptArea = AgentPromptTextField(ProjectManager.getInstance().defaultProject)
      val view = createPaletteView(
        promptArea = promptArea,
        hostMode = AgentPromptPaletteHostMode.INLINE_EMPTY_STATE,
      )

      layoutPopupRoot(view.rootPanel)
      val compactPreferredHeight = view.rootPanel.preferredSize.height
      val compactEditorPreferredHeight = view.promptEditorPanel.preferredSize.height

      promptArea.text = promptText(lineCount = 80)
      view.syncInlineSize()
      layoutPopupRoot(view.rootPanel)

      val cappedExtraHeight = JBUI.scale(108)
      assertThat(view.rootPanel.preferredSize.height).isEqualTo(compactPreferredHeight + cappedExtraHeight)
      assertThat(view.promptEditorPanel.preferredSize.height).isEqualTo(compactEditorPreferredHeight + cappedExtraHeight)
      assertThat(bottomInRoot(view.generationSettingsPanel, view.rootPanel)).isLessThanOrEqualTo(view.rootPanel.height)
    }
  }

  @Test
  fun popupPromptTextDoesNotAutoGrowRoot() {
    runInEdtAndWait {
      val promptArea = AgentPromptTextField(ProjectManager.getInstance().defaultProject)
      val view = createPaletteView(promptArea = promptArea)

      layoutPopupRoot(view.rootPanel)
      val compactPreferredHeight = view.rootPanel.preferredSize.height

      promptArea.text = promptText(lineCount = 80)
      view.syncInlineSize()
      layoutPopupRoot(view.rootPanel)

      assertThat(view.rootPanel.preferredSize.height).isEqualTo(compactPreferredHeight)
    }
  }

  @Test
  fun popupContextChipsRenderAllEntriesWithoutOverflowChip() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChips = AgentPromptContextChipsComponent {}
      val entries = createManyContextEntries()
      contextChips.render(entries)
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChips.component,
      )

      layoutPopupRoot(view.rootPanel)

      val cards = contextAttachmentCards(contextChips.component)
      assertThat(cards).hasSize(entries.size)
      assertThat(contextOverflowCards(contextChips.component)).isEmpty()
    }
  }

  @Test
  fun contextChipsDoNotAddOuterTopGap() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChips = AgentPromptContextChipsComponent {}
      contextChips.render(
        listOf(
          createContextEntry(title = "File", body = "src/Main.java"),
          createContextEntry(title = "Symbol", body = "main"),
        )
      )
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChips.component,
      )

      layoutPopupRoot(view.rootPanel)

      val cards = contextAttachmentCards(contextChips.component)
      assertThat(cards).hasSize(2)
      assertThat(yInRoot(cards[0], view.rootPanel)).isEqualTo(yInRoot(contextChips.component, view.rootPanel))
      assertThat(locationInRoot(cards[1], view.rootPanel).x - rightInRoot(cards[0], view.rootPanel)).isEqualTo(JBUI.scale(4))
    }
  }

  @Test
  fun contextChipsUseTypeSpecificIconsAndFullAccessibleNames() {
    runInEdtAndWait {
      val contextChips = AgentPromptContextChipsComponent {}
      contextChips.render(
        listOf(
          createContextEntry(
            rendererId = AgentPromptContextRendererIds.FILE,
            title = "File",
            body = "src/Main.kt",
          ),
          createContextEntry(
            rendererId = AgentPromptContextRendererIds.PATHS,
            title = "Files",
            body = "dir: src",
            payload = AgentPromptPayload.obj(
              "entries" to AgentPromptPayload.arr(
                AgentPromptPayload.obj(
                  "kind" to AgentPromptPayload.str("dir"),
                  "path" to AgentPromptPayload.str("src"),
                )
              )
            ),
          ),
          createContextEntry(
            rendererId = AgentPromptContextRendererIds.SYMBOL,
            title = "Symbol",
            body = "com.example.Main.run",
          ),
          createContextEntry(
            rendererId = AgentPromptContextRendererIds.VCS_COMMITS,
            title = "Commits",
            body = "",
            payload = AgentPromptPayload.obj(
              "entries" to AgentPromptPayload.arr(
                AgentPromptPayload.obj(
                  "hash" to AgentPromptPayload.str("abc12345abcdef"),
                  "subject" to AgentPromptPayload.str("Fix TEST-101 regression"),
                )
              )
            ),
          ),
          createContextEntry(
            rendererId = AgentPromptContextRendererIds.SNIPPET,
            title = "Local Changes",
            body = "Default changelist\n- modified: src/Main.kt",
            itemId = AgentPromptContextItemIds.CHANGES_SELECTION,
            source = "changes",
          ),
          createContextEntry(
            rendererId = AgentPromptContextRendererIds.TEST_FAILURES,
            title = "Tests",
            body = "failed: Suite#testA",
          ),
        )
      )

      val labels = contextAttachmentLabels(contextChips.component)
      assertThat(labels.take(5).map { it.text }).containsExactly(
        "src/Main.kt",
        "src",
        "com.example.Main.run",
        "Fix TEST-101 regression",
        "Changes",
      )
      assertThat(labels[5].text).isNotBlank()
      assertThat(labels.map { it.icon }).containsExactly(
        AllIcons.FileTypes.Any_type,
        AllIcons.Nodes.Folder,
        AllIcons.Nodes.Method,
        AllIcons.Vcs.CommitNode,
        AllIcons.Vcs.Changelist,
        AllIcons.RunConfigurations.TestState.Red2,
      )

      val cards = contextAttachmentCards(contextChips.component)
      assertThat(cards.take(5).map { it.accessibleContext.accessibleName }).containsExactly(
        "File: src/Main.kt",
        "Files: src",
        "Symbol: com.example.Main.run",
        "Commits: Fix TEST-101 regression",
        "Local Changes",
      )
      assertThat(cards[5].accessibleContext.accessibleName).startsWith("Tests")
    }
  }

  @Test
  fun screenshotContextChipUsesThumbnailBeforeGenericSnippetIcon() {
    runInEdtAndWait {
      val item = AgentPromptScreenshotContextItem.buildScreenshotContextItem(
        title = "Pasted Image",
        screenshot = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).apply {
          val graphics = createGraphics()
          try {
            graphics.color = Color.RED
            graphics.fillRect(0, 0, width, height)
          }
          finally {
            graphics.dispose()
          }
        },
        sourceId = "test.screenshot",
        source = "test",
        tempFilePrefix = "agent-prompt-chip-test-",
      )
      try {
        val contextChips = AgentPromptContextChipsComponent {}
        contextChips.render(listOf(ContextEntry(item = item)))

        val label = contextAttachmentLabels(contextChips.component).single()
        assertThat(label.text).isEqualTo("Pasted Image")
        assertThat(label.icon).isNotSameAs(AllIcons.Actions.ListFiles)
      }
      finally {
        AgentPromptScreenshotContextItem.deleteScreenshotContextFileIfPresent(item)
      }
    }
  }

  @Test
  fun enteringPromptTextKeepsSuggestionRowVisibleWithoutChangingPromptHeight() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val suggestions = AgentPromptSuggestionsComponent {}
      suggestions.render(
        listOf(
          AgentPromptSuggestionCandidate(
            id = "tests.fix",
            label = "Fix failing tests",
            promptText = "Fix the failing tests.",
          )
        )
      )
      val view = createPaletteView(promptArea = promptArea, suggestionsPanel = suggestions.component)
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      layoutPopupRoot(view.rootPanel)
      val promptHeightWithSuggestions = foundPromptArea.height
      val generationSettingsHeight = view.generationSettingsPanel.height
      val generationSettingsLocation = locationInRoot(view.generationSettingsPanel, view.rootPanel)

      assertThat(view.suggestionsPanel.isVisible).isTrue()
      assertThat(view.suggestionsPanel.height).isGreaterThan(0)
      assertThat(promptHeightWithSuggestions).isGreaterThan(0)
      assertThat(view.generationSettingsPanel.isVisible).isTrue()
      assertThat(generationSettingsHeight).isGreaterThan(0)

      promptArea.text = "Draft prompt"
      layoutPopupRoot(view.rootPanel)

      assertThat(view.suggestionsPanel.isVisible).isTrue()
      assertThat(view.suggestionsPanel.height).isGreaterThan(0)
      assertThat(foundPromptArea.height).isEqualTo(promptHeightWithSuggestions)
      assertThat(view.generationSettingsPanel.isVisible).isTrue()
      assertThat(view.generationSettingsPanel.height).isEqualTo(generationSettingsHeight)
      assertThat(locationInRoot(view.generationSettingsPanel, view.rootPanel)).isEqualTo(generationSettingsLocation)
    }
  }

  @Test
  fun hidingGenerationSettingsReturnsSpaceToPromptArea() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea = promptArea)
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      layoutPopupRoot(view.rootPanel)
      val promptHeightWithControls = foundPromptArea.height
      assertThat(view.generationSettingsPanel.height).isGreaterThan(0)

      view.generationSettingsPanel.isVisible = false
      layoutPopupRoot(view.rootPanel)

      assertThat(foundPromptArea.height).isGreaterThan(promptHeightWithControls)
    }
  }

  @Test
  fun suggestionStripStaysAbovePromptAreaAndAwayFromComposerContext() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val suggestions = AgentPromptSuggestionsComponent {}
      suggestions.render(
        listOf(
          AgentPromptSuggestionCandidate(
            id = "tests.fix",
            label = "Fix failing tests",
            promptText = "Fix the failing tests.",
          )
        )
      )
      val contextChips = AgentPromptContextChipsComponent {}
      contextChips.render(listOf(createContextEntry(title = "File", body = "src/Main.java")))
      val view = createPaletteView(
        promptArea = promptArea,
        suggestionsPanel = suggestions.component,
        contextChipsPanel = contextChips.component,
      )

      layoutPopupRoot(view.rootPanel)

      val promptAreaLocation = locationInRoot(checkNotNull(findPromptArea(view.rootPanel, promptArea)), view.rootPanel)
      val suggestionsLocation = locationInRoot(view.suggestionsPanel, view.rootPanel)
      val composerContextLocation = locationInRoot(view.composerContextPanel, view.rootPanel)
      val generationSettingsLocation = locationInRoot(view.generationSettingsPanel, view.rootPanel)

      assertThat(suggestionsLocation.y + view.suggestionsPanel.height).isLessThanOrEqualTo(composerContextLocation.y)
      assertThat(composerContextLocation.y + view.composerContextPanel.height).isLessThanOrEqualTo(promptAreaLocation.y)
      assertThat(promptAreaLocation.y + promptArea.height).isLessThanOrEqualTo(generationSettingsLocation.y)
      assertThat(view.generationSettingsPanel.parent).isNotNull()
    }
  }

  private fun createContextEntry(
    title: String,
    body: String,
    rendererId: String = "test",
    payload: AgentPromptPayloadValue = AgentPromptPayloadValue.Obj.EMPTY,
    itemId: String? = null,
    source: String = "test",
  ): ContextEntry {
    return ContextEntry(
      item = AgentPromptContextItem(
        rendererId = rendererId,
        title = title,
        body = body,
        payload = payload,
        itemId = itemId,
        source = source,
      )
    )
  }

  private fun createManyContextEntries(): List<ContextEntry> {
    return (1..12).map { index ->
      createContextEntry(
        title = "File $index",
        body = "community/plugins/agent-workbench/prompt/ui/src/context/VeryLongContextFileName$index.kt",
      )
    }
  }

  private fun promptText(lineCount: Int): String {
    return (1..lineCount).joinToString("\n") { index -> "Line $index" }
  }

  private fun createPaletteView(
    promptArea: EditorTextField,
    suggestionsPanel: JPanel = JPanel(),
    contextChipsPanel: JPanel = JPanel(),
    addContextVisible: Boolean = true,
    hostMode: AgentPromptPaletteHostMode = AgentPromptPaletteHostMode.POPUP,
  ): AgentPromptPaletteView {
    return createAgentPromptPaletteView(
      promptArea = promptArea,
      suggestionsPanel = suggestionsPanel,
      contextChipsPanel = contextChipsPanel,
      onExistingTaskSelected = {},
      hostMode = hostMode,
    ).apply {
      addContextButton.isVisible = addContextVisible
    }
  }

  private fun populateExistingTasks(view: AgentPromptPaletteView, count: Int) {
    repeat(count) { index ->
      val id = "thread-${index + 1}"
      view.existingTaskListModel.addElement(
        ThreadEntry(
          id = id,
          displayText = "Thread $id",
          secondaryText = "just now",
        )
      )
    }
  }

  private fun locationInRoot(component: Component, root: JPanel): java.awt.Point {
    return SwingUtilities.convertPoint(component.parent, component.location, root)
  }

  private fun yInRoot(component: Component, root: JPanel): Int {
    return locationInRoot(component, root).y
  }

  private fun bottomInRoot(component: Component, root: JPanel): Int {
    return yInRoot(component, root) + component.height
  }

  private fun rightInRoot(component: Component, root: JPanel): Int {
    return locationInRoot(component, root).x + component.width
  }

  private fun yCenterInRoot(component: Component, root: JPanel): Int {
    val location = locationInRoot(component, root)
    return location.y + component.height / 2
  }

  private fun componentRowCount(components: List<Component>, root: JPanel): Int {
    return components.map { component -> locationInRoot(component, root).y }.distinct().size
  }

  private fun contextAttachmentCards(root: Component): List<JComponent> {
    return collectComponentsOfType(root, JComponent::class.java).filter { component ->
      component.getClientProperty(CONTEXT_ATTACHMENT_CARD_PROPERTY) == true
    }
  }

  private fun contextOverflowCards(root: Component): List<JComponent> {
    return contextAttachmentCards(root).filter { component ->
      component.getClientProperty(CONTEXT_ATTACHMENT_OVERFLOW_PROPERTY) == true
    }
  }

  private fun contextRemoveButtons(root: Component): List<JButton> {
    return collectComponentsOfType(root, JButton::class.java).filter { button ->
      button.getClientProperty(CONTEXT_ATTACHMENT_REMOVE_PROPERTY) == true
    }
  }

  private fun contextAttachmentLabels(root: Component): List<JBLabel> {
    return contextAttachmentCards(root).map { card ->
      collectComponentsOfType(card, JBLabel::class.java).single()
    }
  }

}
