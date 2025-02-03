// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.suppression

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

@Service(Service.Level.PROJECT)
internal class GradleSuspendTypecheckingService(val project: Project) : Disposable {
  private val projectsFailedToImport : MutableSet<String> = ConcurrentCollectionFactory.createConcurrentSet()

  fun suspendHighlighting(projectPath: String) {
    projectsFailedToImport.add(projectPath)
  }

  fun resumeHighlighting(projectPath: String) {
    PsiManager.getInstance(project).dropResolveCaches()
    projectsFailedToImport.remove(projectPath)
  }

  fun isSuspended(projectPath: String) : Boolean {
    return projectsFailedToImport.contains(projectPath)
  }

  override fun dispose() {
    projectsFailedToImport.clear()
  }
}