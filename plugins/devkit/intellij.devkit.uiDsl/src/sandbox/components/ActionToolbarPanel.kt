// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.SplitButtonAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.SearchTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

private const val TOOLBAR_PLACE: @NonNls String = "UISandbox.ActionToolbar"
private const val OVERFLOW_TOOLBAR_WIDTH = 160
private const val SEARCH_FIELD_WIDTH = 150

@NlsSafe private const val ACTION_CLASS = "AnAction"
@NlsSafe private const val TOGGLE_ACTION_CLASS = "ToggleAction"
@NlsSafe private const val POPUP_GROUP_CALL = "DefaultActionGroup(text, popup = true)"
@NlsSafe private const val SPLIT_BUTTON_CLASS = "SplitButtonAction"
@NlsSafe private const val TEXT_ACTION_KEY = "ActionUtil.SHOW_TEXT_IN_TOOLBAR"
@NlsSafe private const val LABEL_CLASS = "ToolbarLabelAction"
@NlsSafe private const val COMBO_BOX_CLASS = "ComboBoxAction"
@NlsSafe private const val CUSTOM_COMPONENT_CLASS = "CustomComponentAction"

internal class ActionToolbarPanel : UISandboxPanel {

  override val title: String = "ActionToolbar"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.action.toolbar.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.action.toolbar.group.items")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.action.toolbar.items.note"))
        }
        row {
          cell(createToolbar(createItemActions()))
        }
      }

      group(DevkitUiDslBundle.message("sandbox.action.toolbar.group.layout")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.action.toolbar.layout.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.action.toolbar.label.vertical")) {
          cell(createToolbar(createVerticalActions(), horizontal = false))
        }
        row(DevkitUiDslBundle.message("sandbox.action.toolbar.label.overflow")) {
          cell(createToolbar(createOverflowActions(), layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY,
                             width = OVERFLOW_TOOLBAR_WIDTH))
        }
      }
    }
  }

  private fun createItemActions(): DefaultActionGroup {
    val runWithText = SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.run"), AllIcons.Actions.Execute,
                                   TEXT_ACTION_KEY).apply {
      templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }
    return DefaultActionGroup(
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.refresh"), AllIcons.Actions.Refresh, ACTION_CLASS),
      SampleToggleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.show.details"), AllIcons.Actions.PreviewDetails,
                         TOGGLE_ACTION_CLASS),
      createViewOptionsGroup(),
      SampleSplitButtonAction(createRunGroup()),
      Separator.getInstance(),
      runWithText,
      Separator.getInstance(),
      SampleLabelAction(DevkitUiDslBundle.message("sandbox.action.toolbar.label.scope")),
      ScopeComboBoxAction(),
      Separator.getInstance(),
      SearchFieldAction(),
    )
  }

  private fun createViewOptionsGroup(): DefaultActionGroup {
    return SamplePopupGroup(DevkitUiDslBundle.message("sandbox.action.toolbar.action.view.options"), AllIcons.General.Filter).apply {
      add(SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.compact.view")))
      add(SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.detailed.view")))
    }
  }

  private fun createRunGroup(): DefaultActionGroup {
    return DefaultActionGroup(
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.run"), AllIcons.Actions.Execute, SPLIT_BUTTON_CLASS),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.debug"), AllIcons.Actions.StartDebugger, SPLIT_BUTTON_CLASS),
    )
  }

  private fun createVerticalActions(): DefaultActionGroup {
    return DefaultActionGroup(
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.add"), AllIcons.General.Add),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.edit"), AllIcons.Actions.Edit),
      Separator.getInstance(),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.refresh"), AllIcons.Actions.Refresh),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.settings"), AllIcons.General.Settings),
    )
  }

  private fun createOverflowActions(): DefaultActionGroup {
    return DefaultActionGroup(
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.add"), AllIcons.General.Add),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.edit"), AllIcons.Actions.Edit),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.copy"), AllIcons.Actions.Copy),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.find"), AllIcons.Actions.Find),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.refresh"), AllIcons.Actions.Refresh),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.run"), AllIcons.Actions.Execute),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.debug"), AllIcons.Actions.StartDebugger),
      SampleAction(DevkitUiDslBundle.message("sandbox.action.toolbar.action.settings"), AllIcons.General.Settings),
    )
  }

  private fun createToolbar(
    actions: DefaultActionGroup,
    horizontal: Boolean = true,
    layoutStrategy: ToolbarLayoutStrategy? = null,
    width: Int? = null,
  ): JComponent {
    val holder = JPanel(BorderLayout()).apply {
      isOpaque = false
    }
    val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, actions, horizontal)
    toolbar.targetComponent = holder
    layoutStrategy?.let { toolbar.layoutStrategy = it }
    holder.add(toolbar.component, BorderLayout.CENTER)
    if (width != null) {
      holder.preferredSize = Dimension(JBUI.scale(width), toolbar.component.preferredSize.height)
    }
    return holder
  }

  private class SampleAction(
    text: @NlsActions.ActionText String,
    icon: Icon? = null,
    description: @NlsActions.ActionDescription String? = null,
  ) : DumbAwareAction(text, description, icon), TooltipDescriptionProvider {
    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  private class SampleToggleAction(
    text: @NlsActions.ActionText String,
    icon: Icon,
    description: @NlsActions.ActionDescription String,
  ) : DumbAwareToggleAction(text, description, icon), TooltipDescriptionProvider {
    private var selected = false

    override fun isSelected(e: AnActionEvent): Boolean = selected

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      selected = state
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private class SamplePopupGroup(text: @NlsActions.ActionText String, icon: Icon) : DefaultActionGroup(text, true), TooltipDescriptionProvider {
    init {
      templatePresentation.icon = icon
      templatePresentation.description = POPUP_GROUP_CALL
    }
  }

  private class SampleSplitButtonAction(group: DefaultActionGroup) : SplitButtonAction(group), TooltipDescriptionProvider

  private class SampleLabelAction(text: @NlsActions.ActionText String) : ToolbarLabelAction() {
    init {
      templatePresentation.text = text
      templatePresentation.description = LABEL_CLASS
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private class ScopeComboBoxAction : ComboBoxAction() {
    init {
      templatePresentation.description = COMBO_BOX_CLASS
    }

    private val scopes = listOf(
      DevkitUiDslBundle.message("sandbox.action.toolbar.scope.all.files"),
      DevkitUiDslBundle.message("sandbox.action.toolbar.scope.open.files"),
      DevkitUiDslBundle.message("sandbox.action.toolbar.scope.changed.files"),
    )
    private var selectedScope = scopes.first()

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
      return DefaultActionGroup(scopes.map { scope ->
        object : DumbAwareAction(scope) {
          override fun actionPerformed(e: AnActionEvent) {
            selectedScope = scope
          }
        }
      })
    }

    override fun update(e: AnActionEvent) {
      e.presentation.text = selectedScope
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private class SearchFieldAction : AnAction(), CustomComponentAction {
    init {
      templatePresentation.text = DevkitUiDslBundle.message("sandbox.action.toolbar.action.search")
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return SearchTextField(false).apply {
        textEditor.emptyText.text = DevkitUiDslBundle.message("sandbox.action.toolbar.action.search")
        textEditor.setToolTipText(HtmlChunk.text(CUSTOM_COMPONENT_CLASS))
        preferredSize = Dimension(JBUI.scale(SEARCH_FIELD_WIDTH), preferredSize.height)
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }
}
