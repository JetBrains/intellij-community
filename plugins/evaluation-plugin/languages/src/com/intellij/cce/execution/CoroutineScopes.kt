package com.intellij.cce.execution

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

@Service(Service.Level.PROJECT)
private class EvaluationPluginScope(val scope: CoroutineScope)

fun <T> runSuspendAction(project: Project, action: suspend () -> T): T {
  return runBlockingCancellable {
    val scope = project.serviceAsync<EvaluationPluginScope>().scope
    scope.async { action() }.await()
  }
}
