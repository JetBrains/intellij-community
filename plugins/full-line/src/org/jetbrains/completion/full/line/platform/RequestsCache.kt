package org.jetbrains.completion.full.line.platform

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.completion.full.line.AnalyzedFullLineProposal

class RequestsCache {
  private var key: CacheKey? = null
  private var value: MutableList<AnalyzedFullLineProposal> = mutableListOf()

  fun getOrInitializeCache(filepath: String, offset: Int): MutableList<AnalyzedFullLineProposal> {
    if (!Registry.`is`("full.line.enable.caching")) {
      return mutableListOf()
    }

    val candidate = CacheKey(filepath, offset)
    if (candidate == key) {
      return value
    }

    key = candidate
    value = mutableListOf()
    return value
  }

  private data class CacheKey(private val filepath: String, private val offset: Int)
}
