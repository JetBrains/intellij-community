// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import java.util.function.Supplier

class PresentationAssistantQuickSettingsGroup: DefaultActionGroup(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return listOfNotNull(PresentationAssistantQuickSettingsSizeGroup(),
                         PresentationAssistantQuickSettingsPositionGroup(),
                         if (PresentationAssistant.isThemeEnabled) PresentationAssistantQuickSettingsThemeGroup() else null,
                         Separator(),
                         PresentationAssistantQuickSettingsOpenSettings()).toTypedArray()
  }
}

internal class PresentationAssistantQuickSettingsSizeGroup: DefaultActionGroup(IdeBundle.message("presentation.assistant.quick.settings.size.group"), PresentationAssistantPopupSize.entries.map {
  PresentationAssistantQuickSettingsSize(it)
}), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  override fun isPopup(): Boolean = true
}

internal class PresentationAssistantQuickSettingsSize(val size: PresentationAssistantPopupSize): DumbAwareToggleAction(size.displayName) {
  private val configuration = service<PresentationAssistant>().configuration

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = configuration.popupSize == size.value

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      val presentationAssistant = service<PresentationAssistant>()
      presentationAssistant.configuration.popupSize = size.value
      presentationAssistant.updatePresenter()
    }
  }
}

internal class PresentationAssistantQuickSettingsPositionGroup: DefaultActionGroup(
  Supplier { IdeBundle.message("presentation.assistant.quick.settings.position.group") },
  PresentationAssistantPopupAlignment.entries.map { PresentationAssistantQuickSettingsPosition(it) }
), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  override fun isPopup(): Boolean = true
}

internal class PresentationAssistantQuickSettingsPosition(val position: PresentationAssistantPopupAlignment): DumbAwareToggleAction(position.displayName) {
  private val configuration = service<PresentationAssistant>().configuration

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean =
    configuration.alignmentIfNoDelta?.let {
      it.y == position.y && it.x == position.x
    } ?: false


  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      configuration.verticalAlignment = position.y
      configuration.horizontalAlignment = position.x
      configuration.resetDelta()
      service<PresentationAssistant>().updatePresenter()
    }
  }
}

internal class PresentationAssistantQuickSettingsThemeGroup: DefaultActionGroup(IdeBundle.message("presentation.assistant.quick.settings.theme.group"), PresentationAssistantTheme.entries.map {
  PresentationAssistantQuickSettingsTheme(it)
}), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  override fun isPopup(): Boolean = true
}

internal class PresentationAssistantQuickSettingsTheme(val theme: PresentationAssistantTheme): DumbAwareToggleAction(theme.displayName) {
  private val configuration = service<PresentationAssistant>().configuration

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = configuration.theme == theme.value

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      configuration.theme = theme.value
      service<PresentationAssistant>().updatePresenter()
    }
  }
}

internal class PresentationAssistantQuickSettingsOpenSettings: DumbAwareAction(IdeBundle.message("presentation.assistant.quick.settings.settings")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ShowSettingsUtil.getInstance().showSettingsDialog(project, PresentationAssistantConfigurable::class.java)
  }
}