// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance.dto

import org.jetbrains.idea.maven.performancePlugin.dto.SdkObject

data class NewGradleProjectDto(
  val projectName: String,
  val asModule: Boolean,
  val gradleDSL: String,
  val parentModuleName: String? = null,
  val sdkObject: SdkObject? = null
)
