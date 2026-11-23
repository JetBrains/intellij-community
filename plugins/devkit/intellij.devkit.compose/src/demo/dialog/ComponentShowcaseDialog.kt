@file:OptIn(ExperimentalLayoutApi::class, ExperimentalJewelApi::class, InternalJewelApi::class)

package com.intellij.devkit.compose.demo.dialog

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.toolWindow.ResizeStripeManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar
import org.jetbrains.jewel.bridge.actionSystem.JewelBridgeActionRegistry
import org.jetbrains.jewel.bridge.actionSystem.closeWith
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.bridge.retrieveArcAsCornerSizeOrDefault
import org.jetbrains.jewel.bridge.theme.default
import org.jetbrains.jewel.bridge.theme.macOs
import org.jetbrains.jewel.bridge.toDpSize
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.shortcut.toSwingKeyStroke
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.samples.showcase.ShowcaseShortcuts
import org.jetbrains.jewel.samples.showcase.views.ComponentsView
import org.jetbrains.jewel.samples.showcase.views.ComponentsViewModel
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import java.awt.Dimension
import javax.swing.JComponent

internal class ComponentShowcaseDialog(project: Project) : DialogWrapper(project) {
  init {
    title = DevkitComposeBundle.message("dialog.title.jewel.components.showcase")
    init()
    contentPanel.border = JBUI.Borders.empty()
    rootPane.border = JBUI.Borders.empty()
  }

  override fun createSouthPanel(): JComponent? = null

  override fun createCenterPanel(): JComponent {
    val viewModel =
      ComponentsViewModel(
        alwaysVisibleScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default(),
        whenScrollingScrollbarVisibility = ScrollbarVisibility.WhenScrolling.macOs(),
      )

    val dialogPanel = compose {
      val uiSettings = UISettings.getInstance()
      ProvideMarkdownStyling { RootLayout(viewModel, uiSettings) }
    }
    registerDialogScopedShowcaseShortcuts(dialogPanel)
    dialogPanel.preferredSize = Dimension(800, 600)
    return dialogPanel
  }

  private fun registerDialogScopedShowcaseShortcuts(dialogPanel: JComponent) {
    // Showcase actions are scoped to this dialog rather than declared in plugin.xml so demo keys never pollute
    // global DevKit user keymaps. ActionUtil.wrap attaches dialog-local CustomShortcutSets without mutating KeymapManager.
    val registry = JewelBridgeActionRegistry(dialogPanel)
    for (definition in ShowcaseShortcuts.definitions(useMacModifiers = SystemInfo.isMac)) {
      registry.register(definition.copy(defaultShortcuts = emptyList())).closeWith(disposable)
      if (definition.defaultShortcuts.isNotEmpty()) {
        val shortcuts =
          definition.defaultShortcuts
            .map { sequence ->
              KeyboardShortcut(sequence.first.toSwingKeyStroke(), sequence.second?.toSwingKeyStroke())
            }
            .toTypedArray()
        ActionUtil.wrap(definition.action.id.value)
          .registerCustomShortcutSet(CustomShortcutSet(*shortcuts), dialogPanel, disposable)
      }
    }
  }

  @Suppress("ViewModelForwarding")
  @Composable
  private fun RootLayout(viewModel: ComponentsViewModel, uiSettings: UISettings) {
    ComponentsView(
      viewModel = viewModel,
      toolbarButtonMetrics =
        remember(uiSettings.compactMode, ResizeStripeManager.isShowNames()) {
          IconButtonMetrics(
            minSize = Toolbar.stripeToolbarButtonSize().toDpSize(),
            // See com.intellij.openapi.wm.impl.SquareStripeButtonLook.getButtonArc
            cornerSize =
              retrieveArcAsCornerSizeOrDefault(
                "Button.ToolWindow.arc",
                CornerSize(if (uiSettings.compactMode) 4.dp else 6.dp),
              ),
            padding =
              Toolbar.stripeToolbarButtonIconPadding(
                /* left = */ true,
                /* showNames = */ ResizeStripeManager.isShowNames(),
              )
                .toPaddingValues(),
            borderWidth = 0.dp,
          )
        },
    )
  }
}
