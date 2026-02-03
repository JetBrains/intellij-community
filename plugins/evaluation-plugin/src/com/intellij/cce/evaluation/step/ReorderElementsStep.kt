// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.core.Features
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.Suggestion
import com.intellij.cce.evaluation.TwoWorkspaceHandler
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.FeaturesSerializer

class ReorderElementsStep(private val config: Config) :
  CreateWorkspaceStep(
    Config.buildFromConfig(config) { evaluationTitle = config.reorder.title },
    ReorderElementsHandler(config.reorder.features, config.actions != null)
  ) {

  override val name: String = "Reorder elements"

  override val description: String = "Reorder elements by features values"

  private class ReorderElementsHandler(
    private val featuresForReordering: List<String>,
    private val isActionProjectDataset: Boolean
  ) : TwoWorkspaceHandler {

    override fun invoke(workspace1: EvaluationWorkspace, workspace2: EvaluationWorkspace, indicator: Progress) {
      if (featuresForReordering.isEmpty()) return

      check(isActionProjectDataset) {
        "Reorder is available only for action-based dataset"
      }

      val files = workspace1.sessionsStorage.getSessionFiles()
      for ((counter, file) in files.withIndex()) {
        indicator.setProgress(file.first, file.first, counter.toDouble() / files.size)
        val fileSessionsInfo = workspace1.sessionsStorage.getSessions(file.first)
        val resultSessions = mutableListOf<Session>()
        for (session in fileSessionsInfo.sessions) {
          val json = workspace1.featuresStorage.getFeatures(session.id, fileSessionsInfo.filePath)
          val features = FeaturesSerializer.deserialize(json)
          val newSession = Session(session.offset, session.expectedText, session.completableLength, session.properties)
          for ((i, lookup) in session.lookups.withIndex()) {
            val elements2features = lookup.suggestions.zip(features[i].element)
            val sortedElements2features = elements2features.sortedWith(compareByMultipleFeatures())
            newSession.addLookup(
              Lookup(
                prefix = lookup.prefix,
                offset = lookup.offset,
                suggestions = sortedElements2features.map { it.first },
                latency = lookup.latency,
                features = Features(features[i].common, sortedElements2features.map { it.second }),
                isNew = lookup.isNew
              )
            )
          }
          resultSessions.add(newSession)
          workspace2.featuresStorage.saveSession(newSession, fileSessionsInfo.filePath)
        }
        workspace2.sessionsStorage.saveSessions(
          fileSessionsInfo.copy(sessions = resultSessions)
        )
      }
      workspace2.sessionsStorage.saveMetadata()
    }

    private fun compareByMultipleFeatures(): Comparator<Pair<Suggestion, Map<String, Any>>> {
      return compareBy(
        *featuresForReordering.map { field ->
          { pair: Pair<Suggestion, Map<String, Any>> ->
            -(pair.second[field] as? String ?: "0").toDouble()
          }
        }.toTypedArray()
      )
    }
  }
}
