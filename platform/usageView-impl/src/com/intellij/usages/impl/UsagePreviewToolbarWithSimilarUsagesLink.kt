// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.find.findUsages.similarity.SimilarUsagesComponent
import com.intellij.find.findUsages.similarity.SimilarUsagesToolbar
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.AnActionLink
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.UsageView
import com.intellij.usages.similarity.clustering.ClusteringSearchSession
import com.intellij.usages.similarity.clustering.UsageCluster
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector.Companion.logLinkToSimilarUsagesLinkFromUsagePreviewClicked
import com.intellij.usages.similarity.usageAdapter.SimilarUsage
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.stream.Collectors
import javax.swing.JPanel

class UsagePreviewToolbarWithSimilarUsagesLink(previewPanel: UsagePreviewPanel,
                                               private val myUsageView: UsageView,
                                               selectedInfos: List<UsageInfo>,
                                               cluster: UsageCluster,
                                               session: ClusteringSearchSession) : JPanel(FlowLayout(FlowLayout.LEFT)), Disposable {
  private var myCluster = cluster
  private var anActionLink = createSimilarUsagesLink(previewPanel, selectedInfos)
  private val myScope = CoroutineScope(Dispatchers.Default)

  init {
    background = UIUtil.getTextFieldBackground()
    myCluster = cluster
    add(anActionLink)
    refreshLink(session, selectedInfos)
  }

  private fun refreshLink(session: ClusteringSearchSession, selectedInfos: List<UsageInfo>) {
    myScope.launch {
      while (true) {
        delay(100)
        myCluster = session.findCluster(ContainerUtil.getFirstItem(selectedInfos))!!
        withContext(Dispatchers.EDT) {
          anActionLink.text = UsageViewBundle.message("similar.usages.show.0.similar.usages.title", myCluster.usages.size - 1)
        }
      }
    }
  }

  private fun createSimilarUsagesLink(previewPanel: UsagePreviewPanel, infos: List<UsageInfo>): AnActionLink {
    val similarUsagesLink = AnActionLink(UsageViewBundle.message("similar.usages.show.0.similar.usages.title", myCluster.usages.size - 1),
                                         createOpenSimilarUsagesAction(previewPanel, infos))
    similarUsagesLink.isVisible = myCluster.usages.size > 1
    return similarUsagesLink
  }

  private fun createOpenSimilarUsagesAction(previewPanel: UsagePreviewPanel,
                                            infos: List<UsageInfo>) = object : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val onlyValidUsages = myCluster.usages.stream().filter { usage: SimilarUsage -> usage.isValid }.collect(
        Collectors.toSet())
      previewPanel.removeAll()
      previewPanel.revalidate()
      previewPanel.releaseEditor()
      val firstSelectedInfo = ContainerUtil.getFirstItem(infos)!!
      logLinkToSimilarUsagesLinkFromUsagePreviewClicked(firstSelectedInfo.project, myUsageView)
      val similarUsagesComponent = SimilarUsagesComponent(myUsageView, firstSelectedInfo, previewPanel)
      previewPanel.add(SimilarUsagesToolbar(similarUsagesComponent,
                                            UsageViewBundle.message("0.similar.usages",
                                                                    onlyValidUsages.size - 1), null,
                                            AnActionLink(UsageViewBundle.message(
                                              "0.similar.usages.back.to.usage.preview",
                                              UIUtil.leftArrow()), object : AnAction() {
                                              override fun actionPerformed(e: AnActionEvent) {
                                                previewPanel.removeAll()
                                                Disposer.dispose(similarUsagesComponent)
                                                previewPanel.updateLayout(infos, myUsageView)
                                              }
                                            })), BorderLayout.NORTH)
      previewPanel.add(similarUsagesComponent.createLazyLoadingScrollPane(onlyValidUsages))
    }
  }

  override fun dispose() {
    myScope.cancel()
  }
}