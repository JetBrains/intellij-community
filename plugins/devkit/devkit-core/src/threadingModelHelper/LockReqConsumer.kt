// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer

interface LockReqConsumer {
  fun onStart(method: SmartPsiElementPointer<PsiMethod>) {}

  fun onPath(path: ExecutionPath) {}

  fun onMessageBusTopic(topic: PsiClass) {}

  fun onSwingComponent(signature: MethodSignature) {}

  fun onDone(result: AnalysisResult) {}
}

class DefaultLockReqConsumer(
  private val method: SmartPsiElementPointer<PsiMethod>,
  private val onUpdate: ((AnalysisResult) -> Unit)? = null
) : LockReqConsumer {

  private val paths = java.util.concurrent.CopyOnWriteArraySet<ExecutionPath>()
  private val topics = java.util.concurrent.CopyOnWriteArraySet<PsiClass>()
  private val swing = java.util.concurrent.CopyOnWriteArraySet<MethodSignature>()

  override fun onStart(method: SmartPsiElementPointer<PsiMethod>) {
    clear()
    publish()
  }

  override fun onPath(path: ExecutionPath) {
    paths.add(path)
    publish()
  }

  override fun onMessageBusTopic(topic: PsiClass) {
    topics.add(topic)
    publish()
  }

  override fun onSwingComponent(signature: MethodSignature) {
    swing.add(signature)
    publish()
  }

  override fun onDone(result: AnalysisResult) {
    publish()
  }

  fun snapshot(): AnalysisResult = AnalysisResult(method, paths.toSet(), topics.toSet(), swing.toSet())

  fun clear() {
    paths.clear()
    topics.clear()
    swing.clear()
  }

  private fun publish() {
    onUpdate?.invoke(snapshot())
  }
}
