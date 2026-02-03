// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleTargetUtil")
@file:ApiStatus.Internal

package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.value.TargetValue
import com.intellij.util.PathMapper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.gradle.util.GradleLog.LOG

fun PathMapper?.maybeConvertToRemote(localPath: String): String {
  if (this?.canReplaceLocal(localPath) == true) {
    return convertToRemote(localPath)
  }
  return localPath
}

fun PathMapper?.maybeConvertToLocal(remotePath: String): String {
  if (this?.canReplaceRemote(remotePath) == true) {
    return convertToLocal(remotePath)
  }
  return remotePath
}

fun <T : Any?> TargetValue<T>?.maybeGetLocalValue(): T? {
  return maybeGetValue(this?.localValue)
}

fun <T : Any?> TargetValue<T>?.maybeGetTargetValue(): T? {
  return maybeGetValue(this?.targetValue)
}

private fun <T : Any?> maybeGetValue(promise: Promise<T>?): T? {
  try {
    return promise?.blockingGet(0)
  }
  catch (e: Exception) {
    LOG.warn("Can not get target value", e)
  }
  return null
}
