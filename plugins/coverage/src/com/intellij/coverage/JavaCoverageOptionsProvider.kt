// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@State(name = "JavaCoverageOptionsProvider", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class JavaCoverageOptionsProvider : PersistentStateComponent<JavaCoverageOptionsProvider.State?> {
  private val state = State()

  var branchCoverage: Boolean by state::myBranchCoverage
  var testTracking: Boolean by state::myTestTracking
  var testModulesCoverage: Boolean by state::myTestModulesCoverage
  var excludeAnnotationPatterns: List<String> by state::myExcludeAnnotationPatterns
  var coverageRunner: CoverageRunner?
    get() = state.myRunnerId?.let { CoverageRunner.getInstanceById(it) }
    set(value) {
      state.myRunnerId = value?.id
    }

  override fun getState(): State = state
  override fun loadState(loaded: State) {
    state.myRunnerId = loaded.myRunnerId
    state.myBranchCoverage = loaded.myBranchCoverage
    state.myTestTracking = loaded.myTestTracking
    state.myTestModulesCoverage = loaded.myTestModulesCoverage
    state.myExcludeAnnotationPatterns = listWithDefaultAnnotations(loaded.myExcludeAnnotationPatterns)
  }

  class State {
    internal var myRunnerId: String? = CoverageRunner.getInstance(JavaCoverageRunner.DEFAULT_RUNNER_CLASS).id
    internal var myBranchCoverage: Boolean = true
    internal var myTestTracking: Boolean = false
    internal var myTestModulesCoverage: Boolean = false
    internal var myExcludeAnnotationPatterns: List<String> = defaultExcludeAnnotationPatterns
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<JavaCoverageOptionsProvider>()

    val defaultExcludeAnnotationPatterns: List<String> = listOf("*Generated*")
    private fun listWithDefaultAnnotations(patterns: List<String>): ArrayList<String> {
      val annotations = LinkedHashSet(defaultExcludeAnnotationPatterns)
      annotations.addAll(patterns)
      return ArrayList(annotations)
    }
  }
}
