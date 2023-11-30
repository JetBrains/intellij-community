// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.GentleFlusherBase
import com.intellij.openapi.wm.*
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.UIBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.swing.Icon

private class IndexesAndVfsFlushIndicatorWidgetFactory : StatusBarWidgetFactory, WidgetPresentationFactory {
  override fun getId(): String = "IndexesAndVfsFlushIndicator"

  override fun getDisplayName(): String = UIBundle.message("status.bar.vfs.and.index.flushing.state.widget.name")

  override fun isEnabledByDefault(): Boolean = false

  override fun isAvailable(project: Project): Boolean = ApplicationManager.getApplication().isInternal
  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = ApplicationManager.getApplication().isInternal
  override fun isConfigurable(): Boolean = ApplicationManager.getApplication().isInternal
  override fun createPresentation(context: WidgetPresentationDataContext, scope: CoroutineScope): WidgetPresentation {
    return IndexesAndVfsFlushIndicatorWidget(context)
  }
}

private class IndexesAndVfsFlushIndicatorWidget(private val context: WidgetPresentationDataContext) : IconWidgetPresentation {
  override fun icon(): Flow<Icon?> = flow {
    while (true) {
      val hasSomethingToFlush = GentleFlusherBase.getRegisteredFlushers().any { it.hasSomethingToFlush() }
      emit(if (hasSomethingToFlush) AnimatedIcon.FS() else AllIcons.Actions.Checked_selected)
      delay(500)
    }
  }

  override suspend fun getTooltipText(): String = UIBundle.message("status.bar.vfs.and.index.flushing.state.widget.name")
}