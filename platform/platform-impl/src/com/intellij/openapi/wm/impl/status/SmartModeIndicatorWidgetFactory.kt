// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.SmartModeScheduler
import com.intellij.openapi.wm.*
import com.intellij.ui.UIBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private const val ID = "SmartModeIndicator"

@ApiStatus.Internal
class SmartModeIndicatorWidgetFactory : StatusBarWidgetFactory, WidgetPresentationFactory {
  override fun getId(): String {
    return ID
  }

  override fun getDisplayName(): String {
    return UIBundle.message("status.bar.smart.mode.indicator.widget.name")
  }

  override fun isEnabledByDefault(): Boolean {
    return false
  }

  override fun isAvailable(project: Project): Boolean = ApplicationManager.getApplication().isInternal
  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = ApplicationManager.getApplication().isInternal
  override fun isConfigurable(): Boolean = ApplicationManager.getApplication().isInternal
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

      delay(500)
    }
  }

  override suspend fun getTooltipText(): String = UIBundle.message("status.bar.smart.mode.indicator.widget.name")
}
