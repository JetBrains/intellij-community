// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import org.gradle.tooling.Failure
import org.gradle.tooling.internal.consumer.DefaultFailure
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable
import java.lang.reflect.Method

@ApiStatus.Internal
data class GradleModelFetchFailure(
  val message: String?,
  val description: String?,
  val causes: List<GradleModelFetchFailure>,
) : Serializable {

  constructor(failure: Failure): this(
    failure.message,
    failure.resolveOwnDescription() ?: failure.description,
    failure.causes.map { GradleModelFetchFailure(it) }
  )
}

/**
 * `Failure.getDescription` rebuilds the full text of the whole cause subtree, so the
 * same tail text gets duplicated at every tree level.
 * [DefaultFailure] additionally holds the per-node-only "own description" behind a private method,
 * not yet exposed on the public [Failure] API.
 */
private fun Failure.resolveOwnDescription(): String? {
  val method = ownDescriptionMethod ?: return null
  if (method.declaringClass.isInstance(this)) {
    try {
      return method.invoke(this) as? String?
    }
    catch (_: ReflectiveOperationException) {
    }
  }
  return null
}

private val ownDescriptionMethod: Method? by lazy {
  try {
    Class.forName(DefaultFailure::class.java.name)
      .getDeclaredMethod("getOwnDescription")
      .apply { isAccessible = true }
  }
  catch (_: ReflectiveOperationException) {
    null
  }
}
