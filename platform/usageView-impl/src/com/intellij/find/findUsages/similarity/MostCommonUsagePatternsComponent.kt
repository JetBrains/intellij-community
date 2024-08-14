// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity

import com.intellij.find.findUsages.similarity.UsageCodeSnippetComponent.calculateSnippetRenderingData
import com.intellij.find.findUsages.similarity.UsagePreviewComponent.Companion.create
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageView
import com.intellij.usages.impl.UsageViewImpl
import com.intellij.usages.similarity.clustering.ClusteringSearchSession
import com.intellij.usages.similarity.clustering.UsageCluster
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector.logMoreSnippetsLoadedInClustersPreview
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector.logMostCommonUsagePatternsRefreshClicked
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector.logMostCommonUsagePatternsShown
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector.logShowSimilarUsagesLinkClicked
import com.intellij.usages.similarity.usageAdapter.SimilarUsage
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener.Companion.install
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*
import java.awt.event.ActionListener
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
internal class MostCommonUsagePatternsComponent(
  private val usageView: UsageViewImpl,
  private val session: ClusteringSearchSession,
) : SimpleToolWindowPanel(true), Disposable {

  private val project: Project get() = usageView.project
  private val scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  private class SimilarUsages(val info: UsageInfo, val usagesToRender: Set<SimilarUsage>)
  private class LoadedSnippet(
    val usageInfo: UsageInfo,
    val renderingData: SnippetRenderingData,
    val clusterUsages: Set<SimilarUsage>,
  )

  private val _refreshRequests: MutableSharedFlow<Unit> = MutableSharedFlow()
  private val _selectedUsages: MutableStateFlow<Set<Usage>> = MutableStateFlow(usages())
  private val _loadedSnippets: MutableStateFlow<List<LoadedSnippet>> = MutableStateFlow(emptyList())
  private val _loadSnippetRequests: MutableSharedFlow<Unit> = MutableSharedFlow(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  private val _similarUsages: MutableStateFlow<SimilarUsages?> = MutableStateFlow(null)

  private val myMainPanel: JBPanelWithEmptyText = JBPanelWithEmptyText().also {
    it.layout = VerticalLayout(0)
    it.background = UIUtil.getTextFieldBackground()
  }
  private val myMostCommonUsageScrollPane: JScrollPane
  private val myRefreshAction: AnAction

  init {
    myMostCommonUsageScrollPane = createLazyLoadingScrollPane()
    myRefreshAction = object : AnAction(
      IdeBundle.messagePointer("action.refresh"),
      IdeBundle.messagePointer("action.refresh"),
      AllIcons.Actions.Refresh,
    ) {
      override fun actionPerformed(e: AnActionEvent) {
        logMostCommonUsagePatternsRefreshClicked(project, this@MostCommonUsagePatternsComponent.usageView)
        scope.launch {
          _refreshRequests.emit(Unit)
        }
      }
    }

    if (Registry.`is`("similarity.find.usages.view.auto.update")) {
      scope.launch {
        var previousUsagesCount = 0
        _similarUsages.collectLatest {
          if (it != null) {
            return@collectLatest
          }
          while (true) {
            val usagesCount = usageView.usagesCount
            if (usagesCount != previousUsagesCount) {
              previousUsagesCount = usagesCount
              _refreshRequests.emit(Unit)
            }
            delay(1000)
          }
        }
      }
    }

    scope.launch {
      _refreshRequests.collectLatest {
        handleRefreshRequest()
      }
    }

    scope.launch(Dispatchers.EDT) {
      _similarUsages.collectLatest {
        handleSimilarUsages(it)
      }
    }

    scope.launch(Dispatchers.EDT) {
      renderSnippets()
    }
  }

  private suspend fun handleSimilarUsages(similarUsages: SimilarUsages?) {
    removeAll()
    if (similarUsages == null) {
      setContent(myMostCommonUsageScrollPane)
      updateToolbar(_selectedUsages.value.size)
      return
    }
    val similarComponent = SimilarUsagesComponent(usageView, similarUsages.info)
    try {
      toolbar = SimilarUsagesToolbar(
        similarComponent,
        UsageViewBundle.message("0.similar.usages", similarUsages.usagesToRender.size - 1),
        myRefreshAction,
        ActionLink(
          UsageViewBundle.message("0.similar.usages.back.to.search.results", UIUtil.leftArrow()),
          ActionListener { _similarUsages.value = null }
        )
      )
      setContent(similarComponent.createLazyLoadingScrollPane(similarUsages.usagesToRender))
      awaitCancellation()
    }
    finally {
      Disposer.dispose(similarComponent)
    }
  }

  /**
   * suspends while similar usages panel is shown
   */
  private suspend fun awaitSimilarUsagesHidden() {
    _similarUsages.first {
      it == null
    }
  }

  private suspend fun renderSnippets() {
    val previewComponents: MutableList<UsagePreviewComponent> = CopyOnWriteArrayList()
    try {
      _selectedUsages.collectLatest { selectedUsages ->
        _loadedSnippets.collect { snippets: List<LoadedSnippet> ->
          awaitSimilarUsagesHidden()
          updateToolbar(selectedUsages.size)
          for (i in snippets.size until previewComponents.size) {
            val component = previewComponents[i]
            myMainPanel.remove(component)
            Disposer.dispose(component)
          }
          if (snippets.size < previewComponents.size) {
            previewComponents.subList(snippets.size, previewComponents.size).clear()
          }
          for ((index, loadedSnippet) in snippets.withIndex()) {
            val previewComponent: UsagePreviewComponent?
            if (index < previewComponents.size) {
              previewComponent = previewComponents[index]
              //maybe readaction
              writeIntentReadAction {
                previewComponent.renderCluster(loadedSnippet.usageInfo, loadedSnippet.renderingData)
              }
            }
            else {
              previewComponent = writeIntentReadAction { create(usageView, loadedSnippet.usageInfo, loadedSnippet.renderingData, this) }
              myMainPanel.add(previewComponent)
              previewComponents.add(previewComponent)
            }
            if (loadedSnippet.clusterUsages.size > 1) {
              previewComponent.header.add(createOpenSimilarUsagesActionLink(
                SimilarUsages(loadedSnippet.usageInfo, loadedSnippet.clusterUsages)
              ))
            }
          }
        }
      }
    }
    finally {
      for (previewComponent in previewComponents) {
        Disposer.dispose(previewComponent)
      }
    }
  }

  private fun usages(): Set<Usage> = usageView.selectedUsages

  fun loadSnippets() {
    logMostCommonUsagePatternsShown(project, usageView)
    scope.launch {
      _refreshRequests.emit(Unit)
    }
  }

  private fun updateToolbar(usagesSize: Int) {
    val toolbar = MostCommonUsagesToolbar(
      myMostCommonUsageScrollPane,
      UsageViewBundle.message("similar.usages.0.results", usagesSize),
      myRefreshAction
    )
    if (Registry.`is`("similarity.import.clustering.results.action.enabled")) {
      add(ExportClusteringResultActionLink(
        project, session, StringUtilRt.notNullize(usageView.targets[0].name, "features")
      ))
      add(ImportClusteringResultActionLink(project, session, myRefreshAction))
    }
    this.toolbar = null
    this.toolbar = toolbar
  }

  private fun createLazyLoadingScrollPane(): JScrollPane {
    val lazyLoadingScrollPane = ScrollPaneFactory.createScrollPane(myMainPanel, true)
    lazyLoadingScrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
    install(lazyLoadingScrollPane.verticalScrollBar, threshold = 0.99f) {
      check(_loadSnippetRequests.tryEmit(Unit))
    }
    return lazyLoadingScrollPane
  }

  private suspend fun handleRefreshRequest() {
    awaitSimilarUsagesHidden()
    val selectedUsages = withContext(Dispatchers.EDT) {
      usages()
    }
    _selectedUsages.value = selectedUsages

    val sortedClusters: List<UsageCluster> = readAction {
      session.getClustersForSelectedUsages(selectedUsages)
    }

    val nonClusteredUsages: List<Usage> = selectedUsages.filter {
      it !is SimilarUsage
    }
    coroutineScope {
      @OptIn(ExperimentalCoroutinesApi::class)
      val queue: ReceiveChannel<LoadedSnippet> = produce(capacity = CLUSTER_LIMIT) {
        for (cluster in sortedClusters) {
          val description = readAction {
            renderClusterDescription(cluster)
          }
          if (description != null) {
            send(description)
          }
        }
        for (usage in nonClusteredUsages) {
          val description = readAction {
            renderNonClusteredUsage(usage)
          }
          if (description != null) {
            send(description)
          }
        }
      }

      val initialBatchSize = _loadedSnippets.value.size.coerceAtLeast(CLUSTER_LIMIT)
      _loadedSnippets.value = queue.receiveBatch(initialBatchSize)
      _loadSnippetRequests.debounce(10.milliseconds).collect {
        val newBatch = queue.receiveBatch(CLUSTER_LIMIT)
        if (newBatch.isNotEmpty()) {
          _loadedSnippets.update {
            it + newBatch
          }
          logMoreSnippetsLoadedInClustersPreview(project, usageView, _loadedSnippets.value.size)
        }
      }
    }
  }

  private suspend fun <T> ReceiveChannel<T>.receiveBatch(size: Int): List<T> {
    val batch = ArrayList<T>(size)
    repeat(size) {
      val next = receiveCatching().getOrNull()
      if (next != null) {
        batch.add(next)
      }
      else {
        return batch
      }
    }
    return batch
  }

  private fun createOpenSimilarUsagesActionLink(similarUsages: SimilarUsages): ActionLink {
    val actionLink = ActionLink(
      UsageViewBundle.message("similar.usages.show.0.similar.usages.title", similarUsages.usagesToRender.size - 1),
      ActionListener {
        logShowSimilarUsagesLinkClicked(project, usageView)
        _similarUsages.value = similarUsages
      }
    )
    actionLink.setLinkIcon()
    return actionLink
  }

  override fun dispose() {
    scope.cancel()
  }

  private fun renderClusterDescription(cluster: UsageCluster): LoadedSnippet? {
    val clusterUsages = cluster.usages
    val firstUsage = clusterUsages.firstOrNull()
    val usageInfo = firstUsage?.usageInfo
    val element = usageInfo?.element
    val virtualFile = usageInfo?.virtualFile
    if (usageInfo != null && usageInfo.isValid && element != null && virtualFile != null) {
      val renderingData = calculateSnippetRenderingData(element, usageInfo.rangeInElement)
      if (renderingData != null) {
        return LoadedSnippet(usageInfo, renderingData, clusterUsages)
      }
    }
    return null
  }

  private fun renderNonClusteredUsage(usage: Usage): LoadedSnippet? {
    if (usage !is UsageInfo2UsageAdapter) {
      return null
    }
    if (!usage.isValid()) {
      return null
    }
    val info = usage.usageInfo
    val element = info.element
    val virtualFile = info.virtualFile
    if (element != null && virtualFile != null) {
      val renderingData = calculateSnippetRenderingData(element, info.rangeInElement)
      if (renderingData != null) {
        return LoadedSnippet(info, renderingData, emptySet())
      }
    }
    return null
  }

  companion object {
    private const val CLUSTER_LIMIT = 10

    @JvmStatic
    fun findClusteringSessionInUsageView(usageView: UsageView): ClusteringSearchSession? {
      return usageView.usages.filterIsInstance<SimilarUsage>().map { e: Usage -> (e as SimilarUsage).clusteringSession }.firstOrNull()
    }
  }
}