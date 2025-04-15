// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.statusBar

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.InternalUICustomization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class ProgressPlaceChecker private constructor() {
  companion object {
    val placeChecker = ProgressPlaceChecker()
    fun getInstance(): ProgressPlaceChecker {
      return placeChecker
    }
  }

  private val isSingleStripe = InternalUICustomization.getInstance()?.isSingleStripe() == true
  private val placeFlow = MutableStateFlow(ProgressPlace.UNAVAILABLE)
  private val showInEditorFlow = MutableStateFlow<Boolean>(false)

  val showInEditor: StateFlow<Boolean> = showInEditorFlow

  init {

    if (isSingleStripe) {
      val application = ApplicationManager.getApplication()
      application.messageBus.connect(application).subscribe(UISettingsListener.TOPIC, UISettingsListener {
        checkProgressPlace()
      })

      checkProgressPlace()
    }
  }

  private fun checkProgressPlace() {
    val instance = UISettings.getInstance()
    val place = if (!isSingleStripe) {
      ProgressPlace.UNAVAILABLE
    }
    else {
      if (instance.showProgressesInEditor || instance.presentationMode) ProgressPlace.IN_EDITOR
      else ProgressPlace.IN_STATUS_BAR
    }
    placeFlow.tryEmit(place)
    showInEditorFlow.tryEmit(place == ProgressPlace.IN_EDITOR)
  }

  enum class ProgressPlace {
    IN_EDITOR,
    IN_STATUS_BAR,
    UNAVAILABLE
  }
}