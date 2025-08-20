package com.intellij.grazie.rule

import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.openapi.project.Project

internal class CloudOrLocalBatchParser<T>(private val project: Project, private val cloud: SentenceBatcher.AsyncBatchParser<T>,
                                          private var local: () -> SentenceBatcher.AsyncBatchParser<T>?) : SentenceBatcher.AsyncBatchParser<T> {

  override suspend fun parseAsync(sentences: List<SentenceWithExclusions>): LinkedHashMap<SentenceWithExclusions, T?> {
    if (!GrazieCloudConnector.seemsCloudConnected()) {
      return parseLocal(sentences)
    }
    return cloud.parseAsync(sentences)
  }

  private suspend fun parseLocal(sentences: List<SentenceWithExclusions>): LinkedHashMap<SentenceWithExclusions, T?> {
    return local()?.parseAsync(sentences) ?: LinkedHashMap()
  }
}