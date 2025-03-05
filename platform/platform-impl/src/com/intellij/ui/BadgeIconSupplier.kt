// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.util.ui.JBUI.CurrentTheme.IconBadge
import javax.swing.Icon

/**
 * Provides icon variants with additional badges such as error, warning, information, and success on top of the original icon.
 * It also decorates icons with a live indicator.
 *
 * @property originalIcon The original icon to which badges are to be applied.
 */
class BadgeIconSupplier(val originalIcon: Icon) {
  private val oldLiveIndicatorIcon by lazy { ExecutionUtil.getLiveIndicator(originalIcon) }

  val errorIcon: Icon by lazy { IconManager.getInstance().withIconBadge(originalIcon, IconBadge.ERROR) }
  val warningIcon: Icon by lazy { IconManager.getInstance().withIconBadge(originalIcon, IconBadge.WARNING) }
  val infoIcon: Icon by lazy { IconManager.getInstance().withIconBadge(originalIcon, IconBadge.INFORMATION) }
  val successIcon: Icon by lazy { IconManager.getInstance().withIconBadge(originalIcon, IconBadge.SUCCESS) }
  val liveIndicatorIcon: Icon
    get() = if (ExperimentalUI.isNewUI()) successIcon else oldLiveIndicatorIcon

  fun getErrorIcon(error: Boolean): Icon = if (error) errorIcon else originalIcon
  fun getWarningIcon(warning: Boolean): Icon = if (warning) warningIcon else originalIcon
  fun getInfoIcon(info: Boolean): Icon = if (info) infoIcon else originalIcon
  fun getSuccessIcon(success: Boolean): Icon = if (success) successIcon else originalIcon
  fun getLiveIndicatorIcon(alive: Boolean): Icon = if (alive) liveIndicatorIcon else originalIcon
}
