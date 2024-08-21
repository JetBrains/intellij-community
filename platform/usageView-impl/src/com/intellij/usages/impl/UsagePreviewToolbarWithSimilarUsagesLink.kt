// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.find.findUsages.similarity.SimilarUsagesComponent
import com.intellij.find.findUsages.similarity.SimilarUsagesToolbar
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.AnActionLink
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.UsageView
import com.intellij.usages.similarity.clustering.ClusteringSearchSession
import com.intellij.usages.similarity.clustering.UsageCluster
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector.logLinkToSimilarUsagesLinkFromUsagePreviewClicked
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JPanel

@Internal
class UsagePreviewToolbarWithSimilarUsagesLink(
  private val previewPanel: UsagePreviewPanel,
  private val usageView: UsageView,
  private val infos: List<UsageInfo>,
  private var cluster: UsageCluster,
  session: ClusteringSearchSession,
) : JPanel(FlowLayout(FlowLayout.LEFT)), Disposable {

  private val scope = CoroutineScope(Dispatchers.Default)

  private val showSimilarUsagesActionLink: AnActionLink = run {
    val link = AnActionLink(
      text = UsageViewBundle.message("similar.usages.show.0.similar.usages.title", cluster.usages.size - 1),
      anAction = OpenSimilarUsagesAction()
    )
    link.isVisible = cluster.usages.size > 1
    link
  }

  init {
    background = UIUtil.getTextFieldBackground()
    add(showSimilarUsagesActionLink)
    refreshLink(session, infos)
  }

  private fun refreshLink(session: ClusteringSearchSession, selectedInfos: List<UsageInfo>) {
    scope.launch {
      while (true) {
        delay(100)
        cluster = session.findCluster(ContainerUtil.getFirstItem(selectedInfos))!!
        withContext(Dispatchers.EDT) {
          showSimilarUsagesActionLink.text = UsageViewBundle.message("similar.usages.show.0.similar.usages.title", cluster.usages.size - 1)
        }
      }
    }
  }

  private inner class OpenSimilarUsagesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
      previewPanel.removeAll()
      previewPanel.revalidate()
      previewPanel.releaseEditor()

      val firstSelectedInfo = ContainerUtil.getFirstItem(infos)!!
      logLinkToSimilarUsagesLinkFromUsagePreviewClicked(firstSelectedInfo.project, usageView)

      val similarUsagesComponent = SimilarUsagesComponent(usageView, firstSelectedInfo)
      Disposer.register(previewPanel, similarUsagesComponent)

      val onlyValidUsages = cluster.usages.filterTo(mutableSetOf()) { it.isValid }

      val backActionLink = AnActionLink(
        text = UsageViewBundle.message("0.similar.usages.back.to.usage.preview", UIUtil.leftArrow()),
        anAction = object : AnAction() {
          override fun actionPerformed(e: AnActionEvent) {
            previewPanel.removeAll()
            Disposer.dispose(similarUsagesComponent)
            previewPanel.updateLayout(firstSelectedInfo.project, infos, usageView)
          }
        }
      )

      val similarUsagesToolbar = SimilarUsagesToolbar(
        /* targetComponent = */ similarUsagesComponent,
        /* text = */ UsageViewBundle.message("0.similar.usages", onlyValidUsages.size - 1),
        /* refreshAction = */ null,
        /* backActionLink = */ backActionLink
      )

      previewPanel.add(similarUsagesToolbar, BorderLayout.NORTH)
      previewPanel.add(similarUsagesComponent.createLazyLoadingScrollPane(onlyValidUsages))
    }
  }

  override fun dispose() {
    scope.cancel()
  }
}