// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.util.ui.JBUI.CurrentTheme.IconBadge
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
@ApiStatus.Experimental
class BadgeIconSupplier(val originalIcon: Icon) {
  private val oldLiveIndicatorIcon by lazy { ExecutionUtil.getLiveIndicator(originalIcon) }

  val errorIcon by lazy { IconManager.getInstance().withIconBadge(originalIcon, IconBadge.ERROR) }
  val warningIcon by lazy { IconManager.getInstance().withIconBadge(originalIcon, IconBadge.WARNING) }
  val infoIcon by lazy { IconManager.getInstance().withIconBadge(originalIcon, IconBadge.INFORMATION) }
  val successIcon by lazy { IconManager.getInstance().withIconBadge(originalIcon, IconBadge.SUCCESS) }
  val liveIndicatorIcon
    get() = if (ExperimentalUI.isNewUI()) successIcon else oldLiveIndicatorIcon

  fun getErrorIcon(error: Boolean) = if (error) errorIcon else originalIcon
  fun getWarningIcon(warning: Boolean) = if (warning) warningIcon else originalIcon
  fun getInfoIcon(info: Boolean) = if (info) infoIcon else originalIcon
  fun getSuccessIcon(success: Boolean) = if (success) successIcon else originalIcon
  fun getLiveIndicatorIcon(alive: Boolean) = if (alive) liveIndicatorIcon else originalIcon
}
