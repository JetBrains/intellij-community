// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.project

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil.getNextPowerOfTwo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.Callable

class IndexableFilesCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(project: Project, indicator: ProgressIndicator): CancellablePromise<out Set<MetricEvent>> {
    return ReadAction.nonBlocking(
      Callable<Set<MetricEvent>> {
        var allIndexableFiles = 0
        var inContentIndexableFiles = 0
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        FileBasedIndex.getInstance().iterateIndexableFiles(ContentIterator { fileOrDir ->
          indicator.checkCanceled()
          if (!fileOrDir.isDirectory && !fileIndex.isExcluded(fileOrDir)) {
            if (fileIndex.isInContent(fileOrDir)) {
              inContentIndexableFiles++
            }
            allIndexableFiles++
          }
          true
        }, project, indicator)
        hashSetOf(
          ALL_INDEXABLE_FILES.metric(getNextPowerOfTwo(allIndexableFiles)),
          CONTENT_INDEXABLE_FILES.metric(getNextPowerOfTwo(inContentIndexableFiles))
        )
      })
      .inSmartMode(project)
      .wrapProgress(indicator)
      .submit(NonUrgentExecutor.getInstance())
  }

  companion object {
    private val GROUP = EventLogGroup("project.indexable.files", 2)
    private val ALL_INDEXABLE_FILES = GROUP.registerEvent("all.indexable.files", EventFields.Int("count"))
    private val CONTENT_INDEXABLE_FILES = GROUP.registerEvent("content.indexable.files", EventFields.Int("count"))
  }
}
