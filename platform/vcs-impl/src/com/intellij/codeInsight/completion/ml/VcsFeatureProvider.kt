// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class VcsFeatureProvider : ElementFeatureProvider {
  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ML completion. VCS feature calculation", 1)

  override fun getName(): String = "vcs"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val psi = element.psiElement
    val psiFile = psi?.containingFile

    psiFile?.viewProvider?.virtualFile?.let { file ->
      val changeListManager = ChangeListManager.getInstance(location.project)
      changeListManager.getChange(file)?.let { change ->
        features["file_state"] = MLFeatureValue.categorical(change.type) // NON-NLS

        if (change.type == Change.Type.MODIFICATION && psi is PsiNameIdentifierOwner) {
          calculateWithTimeout(100, TimeUnit.MILLISECONDS) {
            runReadAction {
              val changedRanges = VcsFacade.getInstance().getChangedTextRanges(location.project, psiFile)
              if (changedRanges.any { psi.textRange?.intersects(it) == true })
                MLFeatureValue.binary(true) else null
            }
          }?.let {
            features["declaration_is_changed"] = it // NON-NLS
          }
        }
      }
    }
    return features
  }

  private fun calculateWithTimeout(timeout: Long, timeUnit: TimeUnit, callable: Callable<MLFeatureValue?>): MLFeatureValue? {
    val future = FutureTask(callable)
    executor.execute(future)
    var result: MLFeatureValue? = null
    try {
      result = future.get(timeout, timeUnit)
    }
    catch (ignored: TimeoutException) { }
    return result
  }
}