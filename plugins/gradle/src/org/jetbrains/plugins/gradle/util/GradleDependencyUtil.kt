// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GradleDependencyUtil {
  fun buildSingleStringDependencyNotation(
    group: String,
    name: String,
    version: String?,
    classifier: String?,
    ext: String?,
  ): String? {
    val base = "$group + \":\" + $name"

    if (version == null) {
      return if (classifier == null && ext == null) base else null
    }

    val withVersion = "$base + \":\" + $version"

    return when {
      classifier != null && ext != null -> "$withVersion + \":\" + $classifier + \"@\" + $ext"
      classifier != null -> "$withVersion + \":\" + $classifier"
      ext != null -> "$withVersion + \"@\" + $ext"
      else -> withVersion
    }
  }
}
