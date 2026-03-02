// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.util.xmlb.Converter
import org.gradle.util.GradleVersion

class GradleVersionXmlConverter : Converter<GradleVersion>() {

  override fun toString(value: GradleVersion): String? {
    return value.version
  }

  override fun fromString(value: String): GradleVersion? {
    return try {
      GradleVersion.version(value)
    }
    catch (_: Exception) {
      return null
    }
  }
}