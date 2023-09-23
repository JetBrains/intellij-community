// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.google.common.collect.HashMultiset
import com.intellij.BundleBase
import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.util.gotoByName.ChooseByNameItem
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent
import com.intellij.ide.util.gotoByName.ListChooseByNameModel
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.intellij.internal.statistic.eventLog.newLogEvent
import com.intellij.internal.statistic.service.fus.collectors.*
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.concurrency.resolvedPromise
import java.util.*

internal class CollectFUStatisticsAction : GotoActionBase(), DumbAware {
  override fun update(e: AnActionEvent) {
    super.update(e)
    if (e.presentation.isEnabled) {
      e.presentation.isEnabled = StatisticsRecorderUtil.isTestModeEnabled(StatisticsDevKitUtil.DEFAULT_RECORDER)
    }
  }

  override fun gotoActionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val projectCollectors = UsageCollectors.PROJECT_EP_NAME.extensionList
    val applicationCollectors = UsageCollectors.APPLICATION_EP_NAME.extensionList

    val collectors = (projectCollectors + applicationCollectors).map(UsageCollectorBean::getCollector)

    val ids = collectors.mapTo(HashMultiset.create()) { it.group.id }
    val items = collectors
      .map { collector ->
        val groupId = collector.group.id
        val className = StringUtil.nullize(collector.javaClass.simpleName, true)
        Item(collector, groupId, className, ids.count(groupId) > 1)
      }

    ContainerUtil.sort(items, Comparator.comparing { it.groupId })
    val model = MyChooseByNameModel(project, items)

    val popup = ChooseByNamePopup.createPopup(project, model, getPsiContext(e))
    popup.setShowListForEmptyPattern(true)

    popup.invoke(object : ChooseByNamePopupComponent.Callback() {
      override fun onClose() {
        if (this@CollectFUStatisticsAction.javaClass == myInAction) myInAction = null
      }

      override fun elementChosen(element: Any) {
        runBackgroundableTask("Collecting statistics", project, true) { indicator ->
          indicator.isIndeterminate = true
          indicator.text2 = (element as Item).usagesCollector.javaClass.simpleName
          showCollectorUsages(project, element, model.useExtendedPresentation, indicator)
        }
      }
    }, ModalityState.current(), false)
  }

  private fun showCollectorUsages(project: Project, item: Item, useExtendedPresentation: Boolean, indicator: ProgressIndicator) {
    if (project.isDisposed) {
      return
    }
    val collector = item.usagesCollector
    val metricsPromise = when (collector) {
      is ApplicationUsagesCollector -> resolvedPromise(collector.getMetrics())
      is ProjectUsagesCollector -> collector.getMetrics(project, indicator)
      else -> throw IllegalArgumentException("Unsupported collector: $collector")
    }

    var result: String
    metricsPromise.onSuccess { metrics ->
      if (useExtendedPresentation) {
        result = makeExtendedPresentation(metrics, collector)
      }
      else {
        result = makeSimplePresentation(metrics)
      }
      val fileType = FileTypeManager.getInstance().getStdFileType("JSON")
      val file = LightVirtualFile(item.groupId, fileType, result)
      ApplicationManager.getApplication().invokeLater {
        FileEditorManager.getInstance(project).openFile(file, true)
      }
    }
  }

  private fun makeExtendedPresentation(metrics: Set<MetricEvent>, collector: FeatureUsagesCollector): String {
    val stringJoiner = StringJoiner(",\n", "[\n", "\n]")
    for (metric in metrics) {
      val metricData = FUStateUsagesLogger.mergeWithEventData(null, metric.data)!!.build()
      val event = newLogEvent("test.session", "build", "bucket", System.currentTimeMillis(), collector.group.id,
                              collector.group.version.toString(), "recorder.version", "event.id", true, metricData)
      val presentation = LogEventSerializer.toString(event)
      stringJoiner.add(presentation)
    }
    return stringJoiner.toString()
  }

  private fun makeSimplePresentation(metrics: Set<MetricEvent>): String {
    val stringJoiner = StringJoiner(",\n", "{\n", "\n}")
    for (metric in metrics) {
      val presentation = SerializationHelper.serializeToSingleLine(metric.data.build())
      stringJoiner.add("\"${metric.eventId}\" : $presentation")
    }
    return stringJoiner.toString()
  }

  private class Item(val usagesCollector: FeatureUsagesCollector,
                     val groupId: String,
                     val className: String?,
                     val nonUniqueId: Boolean) : ChooseByNameItem {
    override fun getName(): String = groupId + if (nonUniqueId) " ($className)" else ""
    override fun getDescription(): String? = className
  }

  private class MyChooseByNameModel(project: Project, items: List<Item>)
    : ListChooseByNameModel<Item>(project, "Enter usage collector group id", "No collectors found", items), DumbAware {

    var useExtendedPresentation: Boolean = false

    override fun getCheckBoxName(): String? = BundleBase.replaceMnemonicAmpersand("&Extended presentation")
    override fun loadInitialCheckBoxState(): Boolean = false
    override fun saveInitialCheckBoxState(state: Boolean) {
      useExtendedPresentation = state
    }

    override fun useMiddleMatching(): Boolean = true
  }
}