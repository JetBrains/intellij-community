package org.jetbrains.completion.full.line.services

import com.intellij.openapi.components.service
import org.jetbrains.completion.full.line.AnalyzedFullLineProposal

interface TabSelectedItemStorage {
  fun saveTabSelected(head: String, proposal: AnalyzedFullLineProposal)

  fun prefixFromPreviousSession(): String

  fun getSavedProposal(): AnalyzedFullLineProposal?

  companion object {
    fun getInstance(): TabSelectedItemStorage = service()
  }
}
