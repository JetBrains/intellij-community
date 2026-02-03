package com.intellij.grazie

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope

@Service
class GrazieScope(private val cs: CoroutineScope) : Disposable {
  override fun dispose() {}

  companion object {
    fun coroutineScope(): CoroutineScope {
      return service<GrazieScope>().cs
    }

    fun getInstance(project: Project? = null): GrazieScope =
      (project ?: application).service<GrazieScope>()
  }
}