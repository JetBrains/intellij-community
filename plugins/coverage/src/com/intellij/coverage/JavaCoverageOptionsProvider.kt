// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage

import com.intellij.coverage.analysis.PackageAnnotator
import com.intellij.openapi.components.*
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.ClassUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

@State(name = "JavaCoverageOptionsProvider", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class JavaCoverageOptionsProvider(private val project: Project) : PersistentStateComponent<JavaCoverageOptionsProvider.State?> {
  private val state = State()

  var ignoreImplicitConstructors: Boolean by state::myIgnoreImplicitConstructors
  var excludeAnnotationPatterns: List<String> by state::myExcludeAnnotationPatterns

  @RequiresBackgroundThread
  fun isGeneratedConstructor(qualifiedName: String, methodSignature: String): Boolean {
    if (state.myIgnoreImplicitConstructors) {
      val psiClass = DumbService.getInstance(project).runReadActionInSmartMode<PsiClass?> {
        ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), qualifiedName)
      }
      return PackageAnnotator.isGeneratedDefaultConstructor(psiClass, methodSignature)
    }
    return false
  }

  override fun getState(): State = state
  override fun loadState(loaded: State) {
    state.myIgnoreImplicitConstructors = loaded.myIgnoreImplicitConstructors
    state.myExcludeAnnotationPatterns = listWithDefaultAnnotations(loaded.myExcludeAnnotationPatterns)
  }

  class State {
    internal var myIgnoreImplicitConstructors: Boolean = true
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
