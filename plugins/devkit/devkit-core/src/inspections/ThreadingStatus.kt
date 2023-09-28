// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.concurrency.annotations.*
import org.jetbrains.uast.UMethod

internal enum class ThreadingStatus(val annotationFqn: String,
                                    val shortName: String) {
  REQUIRES_BGT(RequiresBackgroundThread::class.java.canonicalName, "BGT"),
  REQUIRES_EDT(RequiresEdt::class.java.canonicalName, "EDT"),
  REQUIRES_RL(RequiresReadLock::class.java.canonicalName, "RL"),
  REQUIRES_WL(RequiresWriteLock::class.java.canonicalName, "WL"),
  REQUIRES_RL_ABSENCE(RequiresReadLockAbsence::class.java.canonicalName, "RLA");

  fun getDisplayName() = StringUtilRt.getShortName(annotationFqn)
}

internal fun UMethod.getThreadingStatuses(): Set<ThreadingStatus> {
  val threadingStatuses = mutableSetOf<ThreadingStatus>()
  for (uAnnotation in this.uAnnotations) {
    ThreadingStatus.entries.filterTo(threadingStatuses) { uAnnotation.qualifiedName == it.annotationFqn }
  }

  return threadingStatuses
}