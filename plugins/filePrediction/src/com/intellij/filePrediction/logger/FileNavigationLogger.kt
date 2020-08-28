// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.logger

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource
import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.predictor.FilePredictionCandidate
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal class FileNavigationLogger : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("file.prediction", 8)

    private var session: IntEventField = EventFields.Int("session")
    private var performance: LongListEventField = EventFields.LongList("performance")

    private var opened: CompressedBooleanEventField = CompressedBooleanEventField("opened")
    private var source: CompressedEnumEventField<FilePredictionCandidateSource> =
      CompressedEnumEventField("source", FilePredictionCandidateSource::class.java)

    private var probability: CompressedDoubleEventField = CompressedDoubleEventField("prob")

    private val featureName = EventFields.StringValidatedByEnum("name", "feature_name")
    private val featureValue = EventFields.StringValidatedByEnum("value", "feature_value")
    private var features: ObjectListEventField = ObjectListEventField("features", featureName, featureValue)

    private var candidates: ObjectListEventField = ObjectListEventField(
      "candidates",
      EventFields.AnonymizedPath,
      opened.field,
      source.field,
      probability.field,
      features
    )

    private val cacheCandidates = GROUP.registerEvent("calculated", session, performance, candidates)


    fun logEvent(project: Project,
                 sessionId: Int,
                 opened: FilePredictionCandidate?,
                 candidates: List<FilePredictionCandidate>,
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

    private fun toObject(candidate: FilePredictionCandidate, wasOpened: Boolean): ObjectEventData {
      val data = arrayListOf<EventPair<*>>(
        EventFields.AnonymizedPath.with(candidate.path),
        opened.with(wasOpened),
        source.with(candidate.source)
      )
      if (candidate.probability != null) {
        data.add(probability.with(candidate.probability))
      }
      data.add(features.with(candidate.features.map { toFeaturesList(it.key, it.value) }.toList()))
      return ObjectEventData(*data.toTypedArray())
    }

    private fun toFeaturesList(name: String, value: FilePredictionFeature): ObjectEventData {
      val data = arrayListOf<EventPair<*>>(
        featureName.with(name),
        featureValue.with(value.value.toString())
      )
      return ObjectEventData(*data.toTypedArray())
    }

    private fun toPerformanceMetrics(totalDuration: Long, refsComputation: Long,
                                     openCandidate: FilePredictionCandidate?,
                                     candidates: List<FilePredictionCandidate>): List<Long> {
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