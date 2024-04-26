package com.intellij.ae.database.counters.community

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service
class FeatureUsageDatabaseCountersScopeProvider(private val cs: CoroutineScope) : Disposable {
  companion object {
    fun getDisposable(): Disposable = service<FeatureUsageDatabaseCountersScopeProvider>()
    fun getScope() = service<FeatureUsageDatabaseCountersScopeProvider>().cs

    fun getDisposable(project: Project): Disposable = project.service<FeatureUsageDatabaseCountersProjectScopeProvider>()
    fun getScope(project: Project) = project.service<FeatureUsageDatabaseCountersProjectScopeProvider>().cs
  }

  override fun dispose() {}
}

@Service(Service.Level.PROJECT)
private class FeatureUsageDatabaseCountersProjectScopeProvider(val cs: CoroutineScope) : Disposable {
  override fun dispose() {}
}