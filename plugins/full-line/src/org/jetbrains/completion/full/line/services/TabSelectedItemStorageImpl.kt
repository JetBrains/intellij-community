package org.jetbrains.completion.full.line.services

import org.jetbrains.completion.full.line.AnalyzedFullLineProposal

class TabSelectedItemStorageImpl : TabSelectedItemStorage {
  private var head: String? = null
  private var selectedProposal: AnalyzedFullLineProposal? = null

  override fun saveTabSelected(head: String, proposal: AnalyzedFullLineProposal) {
    this.head = head
    selectedProposal = proposal
  }

  override fun getSavedProposal(): AnalyzedFullLineProposal? {
    val result = selectedProposal

    selectedProposal = null
    head = null

    return result
  }

  override fun prefixFromPreviousSession(): String {
    return head ?: ""
  }
}
