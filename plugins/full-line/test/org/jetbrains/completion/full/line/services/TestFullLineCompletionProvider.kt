package org.jetbrains.completion.full.line.services

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.completion.full.line.RawFullLineProposal
import org.jetbrains.completion.full.line.platform.FullLineCompletionQuery
import org.jetbrains.completion.full.line.providers.FullLineCompletionProvider

class TestFullLineCompletionProvider : FullLineCompletionProvider {
  override fun getVariants(query: FullLineCompletionQuery, indicator: ProgressIndicator): List<RawFullLineProposal> {
    return variants
  }

  override fun getId(): String = "test"

  companion object {
    val variants: MutableList<RawFullLineProposal> = mutableListOf()

    fun clear() {
      variants.clear()
    }
  }
}
