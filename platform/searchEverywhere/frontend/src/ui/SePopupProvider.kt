// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SePopupProvider: SePopupManager {
  private var currentPopup: JBPopup? = null

  fun createPopup(vm: SePopupVm, project: Project): JBPopup {
    closePopup()

    val panel = SePopupContentPane(vm, this)

    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.preferableFocusedComponent)
      .setProject(project)
      .setModalContext(false)
      .setNormalWindowLevel(StartupUiUtil.isWaylandToolkit())
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setCancelKeyEnabled(false)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, LOCATION_SETTINGS_KEY, true)
      .setLocateWithinScreenBounds(false)
      .createPopup()

    Disposer.register(popup, panel)

    currentPopup = popup

    return popup
  }

  companion object {
    const val LOCATION_SETTINGS_KEY: String = "search.everywhere.popup"
  }

  override fun closePopup() {
    currentPopup?.cancel()
    currentPopup = null
  }
}