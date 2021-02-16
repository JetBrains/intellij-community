// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader.getDisabledIcon
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache.COMMITTED_TOPIC
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.INCOMING
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.StatusBarWidgetProvider
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.properties.Delegates.observable

private val LOG = logger<IncomingChangesIndicator>()

class IncomingChangesIndicatorProvider : StatusBarWidgetProvider {
  override fun getWidget(project: Project): StatusBarWidget = IncomingChangesIndicator(project)
}

private class IncomingChangesIndicator(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {
  private var statusBar: StatusBar? = null
  private var isIncomingChangesAvailable = false

  private var incomingChangesCount: Int by observable(0) { _, _, newValue ->
    LOG.debug("Refreshing indicator: $newValue changes")
    statusBar?.updateWidget(ID())
  }

  override fun ID(): String = "IncomingChanges"

  override fun getPresentation(): WidgetPresentation = this

  override fun getIcon(): Icon? {
    if (!isIncomingChangesAvailable) return null // hide widget

    return if (incomingChangesCount > 0) AllIcons.Ide.IncomingChangesOn else getDisabledIcon(AllIcons.Ide.IncomingChangesOn)
  }

  override fun getTooltipText(): String? {
    if (!isIncomingChangesAvailable) return null

    return if (incomingChangesCount > 0) message("incoming.changes.indicator.tooltip", incomingChangesCount)
    else message("changes.no.incoming.changelists.available")
  }

  override fun getClickConsumer(): Consumer<MouseEvent> =
    Consumer {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
      toolWindow?.show { ChangesViewContentManager.getInstance(project).selectContent(INCOMING) }
    }

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar

    val busConnection = project.messageBus.connect(this)
    busConnection.subscribe(COMMITTED_TOPIC, object : CommittedChangesListener {
      override fun incomingChangesUpdated(receivedChanges: List<CommittedChangeList>?) = refresh()
      override fun changesCleared() = refresh()
    })
    busConnection.subscribe(VCS_CONFIGURATION_CHANGED, VcsListener { refresh() })
    busConnection.subscribe(VCS_CONFIGURATION_CHANGED_IN_PLUGIN, VcsListener { refresh() })
  }

  override fun dispose() {
    statusBar = null
  }

  private fun refresh() =
    runInEdt {
      if (project.isDisposed || statusBar == null) return@runInEdt

      isIncomingChangesAvailable = IncomingChangesViewProvider.VisibilityPredicate().`fun`(project)
      incomingChangesCount = if (isIncomingChangesAvailable) getCachedIncomingChangesCount() else 0
    }

  private fun getCachedIncomingChangesCount() = CommittedChangesCache.getInstance(project).cachedIncomingChanges?.size ?: 0
}