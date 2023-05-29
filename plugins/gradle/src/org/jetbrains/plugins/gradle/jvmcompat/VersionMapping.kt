// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.components.BaseState

class VersionMapping() : BaseState() {
  constructor(initialVersionInfo: String, initialGradleInfo: String, initialComment: String? = null) : this() {
    javaVersionInfo = initialVersionInfo
    gradleVersionInfo = initialGradleInfo
    comment = initialComment
  }

  var javaVersionInfo by string()
  var gradleVersionInfo by string()
  var comment by string()
}