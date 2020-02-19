// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.project

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newCounterMetric
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil.getNextPowerOfTwo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.Callable

class IndexableFilesCollector : ProjectUsagesCollector() {
  val log = logger<IndexableFilesCollector>()
  override fun getGroupId(): String {
    return "project.indexable.files"
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
          newCounterMetric("all.indexable.files", getNextPowerOfTwo(allIndexableFiles)),
          newCounterMetric("content.indexable.files", getNextPowerOfTwo(inContentIndexableFiles))
        )
      })
      .inSmartMode(project)
      .cancelWith(indicator)
      .submit(NonUrgentExecutor.getInstance())
  }

}
