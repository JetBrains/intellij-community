// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting.dto

data class GradleTaskInfoDto(val taskName: String, val projectHomeDirName: String? = null, val runFromRunAnything: Boolean = false)
