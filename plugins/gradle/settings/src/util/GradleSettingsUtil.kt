// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings

val Project.gradleSettings: GradleSettings
  get() = GradleSettings.getInstance(this)