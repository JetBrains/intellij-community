// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.headertoolbar.blockingComputeMainActionGroups
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.MacUtil
import kotlinx.coroutines.CoroutineScope
import javax.swing.JFrame

/**
 * @author Alexander Lobas
 */
internal object MacFullScreenControlsManager {
  fun enabled(): Boolean = ExperimentalUI.isNewUI() && Registry.`is`("apple.awt.newFullScreeControls", true)

  fun configureEnable(coroutineScope: CoroutineScope, block: () -> Unit) {
    val rKey = Registry.get("apple.awt.newFullScreeControls")
    System.setProperty(rKey.key, java.lang.Boolean.toString(rKey.asBoolean()))
    rKey.addListener(
      object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          System.setProperty(rKey.key, java.lang.Boolean.toString(rKey.asBoolean()))
          block()
        }
      }, coroutineScope)

    if (enabled()) {
      configureColors()
    }

    if (DistractionFreeModeController.isDistractionFreeModeEnabled() || !UISettings.getInstance().showNewMainToolbar) {
      updateForDistractionFreeMode(true)
    }
  }

  private fun configureColors() {
    val color = JBColor.namedColor("MainWindow.FullScreeControl.Background", JBColor(0x7A7B80, 0x575A5C))
    System.setProperty("apple.awt.newFullScreeControls.background", "${color.rgb}")
  }

  fun updateColors(frame: JFrame) {
    if (enabled()) {
      configureColors()

      Foundation.executeOnMainThread(true, false) {
        val window = MacUtil.getWindowFromJavaWindow(frame)
        val delegate = Foundation.invoke(window, "delegate")
        if (Foundation.invoke(delegate, "respondsToSelector:", Foundation.createSelector("updateColors")).booleanValue()) {
          Foundation.invoke(delegate, "updateColors")
        }
      }
    }
  }

  fun updateForCompactMode() {
    updateForPresentationMode()
  }

  fun updateForPresentationMode() {
    if (enabled()) {
      ApplicationManager.getApplication().invokeLater {
        val frames = getAllFrameWindows()
        Foundation.executeOnMainThread(true, false) {
          val selector = Foundation.createSelector("updateFullScreenButtons")
          for (frameOrTab in frames) {
            val window = MacUtil.getWindowFromJavaWindow((frameOrTab as ProjectFrameHelper).frame)
            val delegate = Foundation.invoke(window, "delegate")
            if (Foundation.invoke(delegate, "respondsToSelector:", selector).booleanValue()) {
              Foundation.invoke(delegate, "updateFullScreenButtons")
            }
          }
        }
      }
    }
  }

  fun configureForLightEdit(enterFullScreen: Boolean) {
    if (enabled()) {
      configureForDistractionFreeMode(enterFullScreen)
    }
  }

  fun configureForEmptyToolbarHeader(enter: Boolean) {
    if (enter) {
      if (enabled() && blockingComputeMainActionGroups(CustomActionsSchema.getInstance()).all { it.first.getChildren(null).isEmpty() }) {
        configureForDistractionFreeMode(true)
      }
    }
    else if (!DistractionFreeModeController.isDistractionFreeModeEnabled() && UISettings.getInstance().showNewMainToolbar) {
      configureForDistractionFreeMode(false)
    }
  }

  private fun configureForDistractionFreeMode(enter: Boolean) {
    if (enter) {
      System.setProperty("apple.awt.distraction.free.mode", "true")
    }
    else {
      System.clearProperty("apple.awt.distraction.free.mode")
    }
  }

  fun updateForNewMainToolbar(show: Boolean) {
    if (enabled()) {
      if (show) {
        if (!DistractionFreeModeController.isDistractionFreeModeEnabled()) {
          configureForDistractionFreeMode(false)
          updateFullScreenButtons(false)
        }
      }
      else {
        configureForDistractionFreeMode(true)
        updateFullScreenButtons(true)
      }
    }
  }

  fun updateForDistractionFreeMode(enter: Boolean) {
    if (enabled()) {
      if (enter) {
        configureForDistractionFreeMode(true)
        updateFullScreenButtons(true)
      }
      else if (UISettings.getInstance().showNewMainToolbar) {
        configureForDistractionFreeMode(false)
        updateFullScreenButtons(false)
      }
    }
  }

  private fun updateFullScreenButtons(enter: Boolean) {
    ApplicationManager.getApplication().invokeLater {
      val frames = getAllFrameWindows()
      Foundation.executeOnMainThread(true, false) {
        val selector = Foundation.createSelector("updateFullScreenButtons:")
        for (frameOrTab in frames) {
          val window = MacUtil.getWindowFromJavaWindow((frameOrTab as ProjectFrameHelper).frame)
          val delegate = Foundation.invoke(window, "delegate")
          if (Foundation.invoke(delegate, "respondsToSelector:", selector).booleanValue()) {
            Foundation.invoke(delegate, "updateFullScreenButtons:", if (enter) 1 else 0)
          }
        }
      }
    }
  }

  private fun getAllFrameWindows() = WindowManager.getInstance().allProjectFrames.filter { it.isInFullScreen }
}