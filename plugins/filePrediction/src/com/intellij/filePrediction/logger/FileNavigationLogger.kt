// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.logger

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource
import com.intellij.filePrediction.predictor.FilePredictionCompressedCandidate
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal class FileNavigationLogger : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("file.prediction", 12)

    private var session: IntEventField = EventFields.Int("session")
    private var performance: LongListEventField = EventFields.LongList("performance")

    private var anonymized_path: CandidateAnonymizedPath = CandidateAnonymizedPath()
    private var opened: EncodedBooleanEventField = EncodedBooleanEventField("opened")
    private var source: EncodedEnumEventField<FilePredictionCandidateSource> = EncodedEnumEventField("source")

    private var probability: EncodedDoubleEventField = EncodedDoubleEventField("prob")

    private var features: StringEventField = EventFields.StringValidatedByCustomRule("features", "file_features")

    private var candidates: ObjectListEventField = ObjectListEventField(
      "candidates",
      anonymized_path,
      opened.field,
      source.field,
      probability.field,
      features
    )

    private val cacheCandidates = GROUP.registerEvent("calculated", session, performance, candidates)


    fun logEvent(project: Project,
                 sessionId: Int,
                 opened: FilePredictionCompressedCandidate?,
                 candidates: List<FilePredictionCompressedCandidate>,
                 totalDuration: Long,
                 refsComputation: Long) {
      val allCandidates: MutableList<ObjectEventData> = arrayListOf()
      if (opened != null) {
        allCandidates.add(toObject(opened, true))
      }

      for (candidate in candidates) {
        allCandidates.add(toObject(candidate, false))
      }

      val performanceMs = toPerformanceMetrics(totalDuration, refsComputation, opened, candidates)
      cacheCandidates.log(project, sessionId, performanceMs, allCandidates)
    }

    private fun toObject(candidate: FilePredictionCompressedCandidate, wasOpened: Boolean): ObjectEventData {
      val data = arrayListOf<EventPair<*>>(
        anonymized_path.with(candidate.path),
        opened.with(wasOpened),
        source.with(candidate.source)
      )
      if (candidate.probability != null) {
        data.add(probability.with(candidate.probability))
      }
      data.add(features.with(candidate.features))
      return ObjectEventData(data)
    }

    private fun toPerformanceMetrics(totalDuration: Long, refsComputation: Long,
                                     openCandidate: FilePredictionCompressedCandidate?,
                                     candidates: List<FilePredictionCompressedCandidate>): List<Long> {
      var featuresMs: Long = 0
      var predictionMs: Long = 0
      openCandidate?.let {
        featuresMs += it.featuresComputation
        predictionMs += it.duration ?: 0
      }

      for (candidate in candidates) {
        featuresMs += candidate.featuresComputation
        predictionMs += candidate.duration ?: 0
      }
      return arrayListOf(totalDuration, refsComputation, featuresMs, predictionMs)
    }
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}