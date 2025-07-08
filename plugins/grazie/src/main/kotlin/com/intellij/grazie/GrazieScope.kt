package com.intellij.grazie

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope

@Service
class GrazieScope(private val cs: CoroutineScope) {
  companion object {
    fun coroutineScope(): CoroutineScope {
      return service<GrazieScope>().cs
    }
  }
}