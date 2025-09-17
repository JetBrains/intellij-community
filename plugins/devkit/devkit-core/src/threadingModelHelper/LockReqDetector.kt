// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass

interface LockReqDetector {

  fun findAnnotationRequirements(method: PsiMethod): List<LockRequirement>

  fun findBodyRequirements(method: PsiMethod): List<LockRequirement>

  fun isAsyncDispatch(method: PsiMethod): Boolean

  fun isMessageBusCall(method: PsiMethod): Boolean

  fun extractMessageBusTopic(method: PsiMethod): PsiClass?
}