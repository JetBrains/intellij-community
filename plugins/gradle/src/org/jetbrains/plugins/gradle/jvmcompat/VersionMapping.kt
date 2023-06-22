// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.components.BaseState

class VersionMapping() : BaseState() {

  var javaVersionInfo by string()
  var gradleVersionInfo by string()

  constructor(java: String, gradle: String) : this() {
    javaVersionInfo = java
    gradleVersionInfo = gradle
  }
}