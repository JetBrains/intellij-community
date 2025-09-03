// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.bfl

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsAnalyzerBFS
import org.jetbrains.idea.devkit.threadingModelHelper.LockType

class BFLReqsChecker(private val analyzer: LockReqsAnalyzerBFS = LockReqsAnalyzerBFS()) {

  data class CheckResult(
    val implementation: PsiClass,
    val canMoveToBackground: Boolean,
    val edtGlobalPaths: List<ExecutionPath> = emptyList(),
  )

  companion object {
    private const val PARENT_CLASS_NAME = "com.intellij.openapi.vfs.newvfs.BulkFileListener"
    private const val BEFORE_METHOD_NAME = "before"
    private const val AFTER_METHOD_NAME = "after"
    private const val MAX_IMPLEMENTATIONS = 10
    private const val TARGET_CLASS_NAME = "com.intellij.codeInsight.ExternalAnnotationsManagerImpl"
  }

  fun runChecker(project: Project): List<AnalysisResult> {
    val targetClass = findTargetImplementation(project)
    return ReadAction.nonBlocking<List<AnalysisResult>> {
      targetClass.allMethods.map { analyzer.analyzeMethod(it) }
    }.executeSynchronously()
  }

  fun findTargetImplementation(project: Project, scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)): PsiClass {
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    val targetClass = javaPsiFacade.findClass(TARGET_CLASS_NAME, scope)!!
    return targetClass
  }

  fun findAllImplementations(project: Project, scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)): List<PsiClass> {
    val implementations = mutableListOf<PsiClass>()
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    val bulkFileListenerClass = javaPsiFacade.findClass(PARENT_CLASS_NAME, scope) ?: return emptyList()
    val query = ClassInheritorsSearch.search(bulkFileListenerClass, scope, true)
    query.allowParallelProcessing().forEach(Processor {
      if (implementations.size >= MAX_IMPLEMENTATIONS) return@Processor false
      implementations.add(it)
    })

    return implementations
  }

  fun checkImplementation(implementation: PsiClass): CheckResult {
    val edtGlobalPaths = mutableListOf<ExecutionPath>()
    val beforeMethodRequiresEDT = checkMethod(implementation, BEFORE_METHOD_NAME, edtGlobalPaths)
    val afterMethodRequiresEDT = checkMethod(implementation, AFTER_METHOD_NAME, edtGlobalPaths)
    val canMoveToBackground = !beforeMethodRequiresEDT && !afterMethodRequiresEDT
    return CheckResult(implementation, canMoveToBackground, edtGlobalPaths)
  }

  private fun checkMethod(implementation: PsiClass, methodName: String, edtGlobalPaths: MutableList<ExecutionPath>): Boolean {
    val methods = implementation.allMethods.filter { it.name == methodName }
    val method = methods.firstOrNull() ?: return false

    val result = analyzer.analyzeMethod(method)
    val edtLocalPaths = result.paths.filter { it.lockRequirement.lockType == LockType.EDT }
    edtGlobalPaths.addAll(edtLocalPaths)
    return edtLocalPaths.isNotEmpty()
  }
}