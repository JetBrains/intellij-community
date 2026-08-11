// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.wm.IconWidgetPresentation
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WidgetPresentation
import com.intellij.openapi.wm.WidgetPresentationDataContext
import com.intellij.openapi.wm.WidgetPresentationFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.UIBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

internal class IndexesAndVfsFlushIndicatorWidgetFactory : StatusBarWidgetFactory, WidgetPresentationFactory {
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
      //TODO RC: Should ask FileBasedIndex.isDirty() also -- but there is no ready-to-use API like that.
      //         Previously GentleIndexesFlusher used private indexes impl methods for it.
      //         Now I don't sure we do really need the Widget at all => unsure does it worth to make FileBasedIndex.isDirty just for it
      val hasSomethingToFlush = FSRecords.getInstance().connection().isDirty
      emit(if (hasSomethingToFlush) AnimatedIcon.FS() else AllIcons.Actions.Checked_selected)
      delay(500.milliseconds)
    }
  }

  override suspend fun getTooltipText(): String = UIBundle.message("status.bar.vfs.and.index.flushing.state.widget.name")
}