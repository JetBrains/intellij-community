// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.PlatformUtils
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

/** The appearance settings of tool windows that a saved layout stores. A null flag keeps the current UI setting. */
@Internal
@Serializable
data class ToolWindowsAppearanceSettings(
  val leftHorizontalSplit: Boolean? = null,
  val rightHorizontalSplit: Boolean? = null,
  val wideScreenSupport: Boolean? = null,
  val showToolWindowsNames: Boolean? = null,
  val hideToolStripes: Boolean? = null,
  val experimentalSingleStripe: Boolean? = null,
  val rememberSizeForEachToolWindowNewUI: Boolean? = null,
) {
  internal companion object {
    val isEnabled: Boolean
      get() = RegistryManager.getInstance().`is`("toolwindow.layout.save.appearance")

    fun fromSettings(settings: UISettings): ToolWindowsAppearanceSettings = ToolWindowsAppearanceSettings(
      leftHorizontalSplit = settings.leftHorizontalSplit,
      rightHorizontalSplit = settings.rightHorizontalSplit,
      wideScreenSupport = settings.wideScreenSupport,
      showToolWindowsNames = settings.showToolWindowsNames,
      hideToolStripes = settings.state.hideToolStripes,
      experimentalSingleStripe = NotRoamableUiSettings.getInstance().experimentalSingleStripe,
      rememberSizeForEachToolWindowNewUI = settings.rememberSizeForEachToolWindowNewUI,
    )

    fun factoryDefault(isNewUi: Boolean): ToolWindowsAppearanceSettings = ToolWindowsAppearanceSettings(
      leftHorizontalSplit = false,
      rightHorizontalSplit = false,
      wideScreenSupport = false,
      showToolWindowsNames = false,
      hideToolStripes = false,
      experimentalSingleStripe = isNewUi && PlatformUtils.isDataGrip(),
      rememberSizeForEachToolWindowNewUI = false,
    )
  }

  /** Applies the non-null flags to [settings] and notifies the listeners if a flag changes. */
  internal fun applyTo(settings: UISettings) {
    val previousSettings = fromSettings(settings)
    leftHorizontalSplit?.let { settings.leftHorizontalSplit = it }
    rightHorizontalSplit?.let { settings.rightHorizontalSplit = it }
    wideScreenSupport?.let { settings.wideScreenSupport = it }
    showToolWindowsNames?.let { settings.showToolWindowsNames = it }
    hideToolStripes?.let { settings.state.hideToolStripes = it }
    // DataGrip enables this flag in DataGripPluginConfigurator.enableSingleStripe().
    // See dbe/datagrip/frontend/customization/src/DataGripFrontendPluginConfigurator.kt.
    if (PlatformUtils.isDataGrip()) {
      experimentalSingleStripe?.let { NotRoamableUiSettings.getInstance().experimentalSingleStripe = it }
    }
    rememberSizeForEachToolWindowNewUI?.let { settings.rememberSizeForEachToolWindowNewUI = it }
    if (previousSettings.showToolWindowsNames != settings.showToolWindowsNames) {
      ResizeStripeManager.applyShowNames()
    }
    if (previousSettings != fromSettings(settings)) {
      settings.fireUISettingsChanged()
    }
  }
}
