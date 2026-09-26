// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.SmartModeScheduler
import com.intellij.openapi.wm.IconWidgetPresentation
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WidgetPresentation
import com.intellij.openapi.wm.WidgetPresentationDataContext
import com.intellij.openapi.wm.WidgetPresentationFactory
import com.intellij.ui.UIBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

private const val ID = "SmartModeIndicator"

internal class SmartModeIndicatorWidgetFactory : StatusBarWidgetFactory, WidgetPresentationFactory {
  override fun getId(): String = ID
  override fun getDisplayName(): String = UIBundle.message("status.bar.smart.mode.indicator.widget.name")
  override fun isEnabledByDefault(): Boolean = false
  override fun isInternal(): Boolean = true

  override fun createPresentation(context: WidgetPresentationDataContext, scope: CoroutineScope): WidgetPresentation {
    return SmartModeIndicatorWidget(context)
  }
}

private class SmartModeIndicatorWidget(private val context: WidgetPresentationDataContext) : IconWidgetPresentation {
  override fun icon(): Flow<Icon?> = flow {
    while (true) {
      val currentMode = context.project.getService(SmartModeScheduler::class.java).getCurrentMode()
      emit(when {
             currentMode >= SmartModeScheduler.DUMB -> AllIcons.Toolwindows.ErrorEvents
             currentMode >= SmartModeScheduler.SCANNING -> AllIcons.Toolwindows.WarningEvents
             currentMode == 0 -> AllIcons.Toolwindows.InfoEvents
             else -> AllIcons.Toolwindows.NoEvents
           })

      delay(500.milliseconds)
    }
  }

  override suspend fun getTooltipText(): String = UIBundle.message("status.bar.smart.mode.indicator.widget.name")
}
