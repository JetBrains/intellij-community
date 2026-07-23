// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

private const val PROPERTY_ID: String = "bazel.project.detector.is.bazel.project"

@ApiStatus.Internal
object BazelProjectDetector {
  fun hasBazelFiles(project: Project): Boolean {
    return PropertiesComponent.getInstance(project).getBoolean(PROPERTY_ID)
  }

  fun setHasBazelFiles(project: Project, hasBazelFiles: Boolean) {
    PropertiesComponent.getInstance(project).setValue(PROPERTY_ID, hasBazelFiles)
  }
}
